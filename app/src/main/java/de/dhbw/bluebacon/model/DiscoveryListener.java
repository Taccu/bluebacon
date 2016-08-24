package de.dhbw.bluebacon.model;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import de.dhbw.bluebacon.BuildConfig;
import de.dhbw.bluebacon.MainActivity;
import de.dhbw.bluebacon.R;

public class DiscoveryListener extends AsyncTask<Void, Void, String> {
    //TODO: Why is this public?
    public static final String LOG_TAG = "DHBW DiscoveryListener";
    public static final int SOCKET_TIMEOUT_MILLIS = 2000;
    public static final String SERVER_URL_TEMPLATE = "http://%s/json.php";

    public AtomicBoolean gotOwnDatagram;

    Context context;
    //TODO: This variable should be not global, as it is only used in listen()-method
    DatagramSocket socket;

    private static final String HMAC_SECRET = "eFqqDnFNeLLJ";

    public DiscoveryListener(Context context){
        this.context = context;
        this.gotOwnDatagram = new AtomicBoolean(false);
    }

    @Override
    protected String doInBackground(Void... params){
        return listen();
    }

    @Override
    protected void onPostExecute(String local_ip) {
        if(local_ip == null){
            Log.i(LOG_TAG, "UDP discovery: we got no answer");
            //TODO: this variable is a waste of ram
            boolean preferRemoteServer = ((MainActivity)context).prefs.getBoolean(MainActivity.PrefKeys.SERVER_LOCATION_PRIORITY.toString(), true);
            if(preferRemoteServer){
                Log.e(LOG_TAG, "No local and/or remote servers could be reached.");
                ((MainActivity)context).progressHide();
                Toast.makeText(context, context.getString(R.string.no_server_found), Toast.LENGTH_LONG).show();
                ((MainActivity)context).updateLastUpdateInfo(false, "-");
                ((MainActivity)context).refreshSettingsUi();
            } else {
                // use JSONLoader within new thread
                Log.i(LOG_TAG, "No local server found, trying remote server...");
                ((MainActivity)context).progressShow(context.getString(R.string.contacting_server));
                new JSONLoader(context).execute();
            }

        } else {
            Log.i(LOG_TAG, "UDP discovery: got answer from: " + local_ip);
            ((MainActivity)context).prefs.edit().putString(MainActivity.PrefKeys.SERVER_ADDR.toString(), local_ip).commit();
            // we have found our server and can contact it via JSONLoader now
            ((MainActivity)context).progressShow(context.getString(R.string.contacting_server));
            new JSONLoader(context, false).execute(String.format(SERVER_URL_TEMPLATE, local_ip));
        }
    }

    // Discovery process
    // #################
    // 1) the server listens (always)
    // 2) discovery starts
    // 3) we listen
    // 4) we broadcast
    // 5) when the server gets our packet, it sends a corresponding unicast packet to us
    // 6) we check the packet
    // 7) if successful or timeout, we stop listening, discovery ends
    // the idea is that the server has to hash our random value such that the client accepts it.
    // this allows clients to associate server responses with their own requests, which gets
    // important in scenarios where multiple clients/servers are performing discovery at the same time.
    // the HMAC secret merely serves as a way to tie discovery responses to discovery requests a little bit more tightly.
    // the HMAC secret should be considered public and is not intended as a means for providing authentication.
    public String listen(){
        try {
            //Keep a socket open to listen to all UDP traffic that is destined for this port
            InetAddress wildCard = new InetSocketAddress(0).getAddress(); // 0.0.0.0, i.e. all interfaces
            socket = new DatagramSocket(DiscoveryBroadcaster.UDP_PORT, wildCard);
            socket.setBroadcast(true);
            socket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);

            //Receive a packet
            byte[] recvBuf = new byte[32];
            DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
            Log.i(LOG_TAG, "Ready to receive packet");

            boolean fromThisHost;
            do{
                socket.receive(packet);
                // ignore our own packets and wait for a next one.
                // make it possible for other threads to check if
                // we got a datagram from ourselves so they know the socket is open.
                fromThisHost = isOwnIpAddress(packet.getAddress());
                if(fromThisHost){
                    this.gotOwnDatagram.compareAndSet(false, true);
                }
            } while(fromThisHost);

            //Packet received
            if(BuildConfig.DEBUG){
                Log.i(LOG_TAG, "Packet with " + packet.getData().length + " bytes data received from: " + packet.getAddress().getHostAddress());
            }

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret = new SecretKeySpec(HMAC_SECRET.getBytes(StandardCharsets.UTF_8),"HmacSHA256");
            mac.init(secret);
            byte[] digest = mac.doFinal(DiscoveryBroadcaster.getLastRandomBytes());
            if(!Arrays.equals(packet.getData(), digest)){
                Log.i(LOG_TAG, "Got invalid identifier");
                return null;
            }
            if(!packet.getAddress().isSiteLocalAddress()) {
                Log.i(LOG_TAG, "Packet did not come from local address!");
                return null;
            }
            return packet.getAddress().getHostAddress();

        } catch (IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            if(BuildConfig.DEBUG && e.getMessage() != null) {
                Log.e(LOG_TAG, e.getMessage());
            }
        } finally {
            socket.close();
        }
        return null;
    }

    public static boolean isOwnIpAddress(InetAddress addr) {
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress()) {
            return true;
        }

        try {
            return NetworkInterface.getByInetAddress(addr) != null;
        } catch (SocketException e) {
            return false;
        }
    }

}
