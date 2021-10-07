package com.example.bluetooth;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.UUID;

public class BluetoothConnectionService {
    private static final String TAG = "BluetoothConnectionServ";

    private static final String appName = "Casco";

    private static final UUID MY_UUID = UUID.fromString("954BA527-FFE0-4CA4-B9BD-1271F669EC13");

    private final BluetoothAdapter mBluetoothAdapter;
    Context mContext;

    private AcceptThread mAcceptThread;

    private ConnectThread mConnectThread;
    private BluetoothDevice mmDevice;
    private UUID deviceUUID;
    ProgressDialog mProgressDialog;

    private ConnectedThread mConnectedThread;

    public BluetoothConnectionService(Context context){
        mContext = context;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    private class AcceptThread extends Thread{ //AcceptThread aspetta una connessione e prova a connettersi

        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread(){
            BluetoothServerSocket tmp = null;

            //Creazione listening server socket
            try{
                tmp = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(appName,MY_UUID);//socket che altri dispositivi si possono connettere
                Log.d(TAG,"AcceptThread: Setting up Server using: "+MY_UUID);
            }catch(IOException e){
                Log.e(TAG,"AcceptThread: IOException: "+e.getMessage());
            }

            mmServerSocket = tmp;

        }

        @Override
        public void run() {
            Log.d(TAG,"run: AcceptThread Running.");
            BluetoothSocket socket = null;

            try{//ritornerà una connessione avvenuta con successo o l'eccezione
                Log.d(TAG,"run: RFCOM server socket start.....");
                socket = mmServerSocket.accept();
                Log.d(TAG,"run: RFCOM server socket accepted connection.");
            }catch (IOException e){
                Log.e(TAG,"AcceptThread: IOException: "+e.getMessage());
            }

            //......
            if (socket!=null){
                connected(socket,mmDevice);
            }

            Log.i(TAG,"END mAcceptThread");

        }

        public void cancel(){
            Log.d(TAG,"cancel: Canceling AcceptThread");
            try{
                mmServerSocket.close();
            }catch (IOException e){
                Log.e(TAG,"cancel: Close of AcceptThread ServerSocket failed. "+e.getMessage());
            }
        }
    }

    public class ConnectThread extends Thread{//questo thread verra eseguito per creare una connessione in uscita con un device
        private BluetoothSocket mmSocket;

        public ConnectThread(BluetoothDevice device,UUID uuid){
            Log.d(TAG,"ConncetThread: started");
            mmDevice = device;
            deviceUUID = uuid;
        }

        @Override
        public void run(){
            BluetoothSocket tmp = null;
            Log.i(TAG,"RUN mConnectThread ");

            //prendi un BluetoothSocket per una connessione con il device dato
            try {
                Log.d(TAG,"ConnectThread: Trying to create InsecureRfcommSocket using UUID: "+MY_UUID);
                tmp = mmDevice.createRfcommSocketToServiceRecord(deviceUUID);
            } catch (IOException e) {
                Log.e(TAG,"ConnectThread: Could not create InsecureRfcommSocket "+e.getMessage());
            }

            mmSocket = tmp;

            mBluetoothAdapter.cancelDiscovery();//Sempre cancellarlo perchè rallenta la connessione

            //Creaiamo una connessione al BluetoothSocket

            try {//ritornerà una connessione avvenuta con successo o l'eccezione
                mmSocket.connect();

                Log.d(TAG,"run: ConnectThread connected.");
            } catch (IOException e) {
                //chiudi il socket
                try {
                    mmSocket.close();
                    Log.d(TAG,"run: Closed Socket");
                } catch (IOException ioException) {
                    Log.e(TAG,"mConncetThread: run: Unable to close connection in socket "+ ioException.getMessage());
                }
                Log.d(TAG,"run: ConnectThread: Could not connect to UUID: "+MY_UUID);
            }
            //..........
            connected(mmSocket,mmDevice);
        }

        public void cancel(){
            try{
                Log.d(TAG,"cancel: Closing Client Socket.");
                mmSocket.close();
            }catch (IOException e){
                Log.e(TAG,"cancel: close() of mmSocket in ConnectThread failed "+e.getMessage());
            }
        }
    }

    public synchronized void start(){
        Log.d(TAG,"start");

        if(mConnectThread!=null){//cancello qualsiasi thread che sta già creando una connessione
            mConnectThread.cancel();
            mConnectThread = null;
        }
        if (mAcceptThread == null){
            mAcceptThread = new AcceptThread();
            mAcceptThread.start();
        }
    }

    //AcceptThread inizia ed aspetta per una connessione
    //Poi ConnectThread inizia e cerca di creare una connessione con gli altri device AcceptThread

    public void startClient(BluetoothDevice device,UUID uuid){
        mProgressDialog = ProgressDialog.show(mContext,"Connect Bluetooth","Please wait...",true);
        mConnectThread = new ConnectThread(device,uuid);
        mConnectThread.start();
    }

    private class ConnectedThread extends Thread{
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket){
            Log.d(TAG, "ConnectedThread: Starting.");

            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // una volta che la connessione si è stabilita si può chiudere il ProgressDialog
            mProgressDialog.dismiss();

            try{
                tmpIn = mmSocket.getInputStream();
                tmpOut = mmSocket.getOutputStream();
            }catch (IOException e){
                e.printStackTrace();
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        @Override
        public void run(){
            byte[] buffer = new byte[1024]; //buffer store for the stream

            int bytes;//bytes returned from read()

            while (true){
                try {
                    bytes = mmInStream.read(buffer);
                    String incomingMessage = new String(buffer,0,bytes);
                    Log.d(TAG,"InputStream: "+incomingMessage);
                } catch (IOException e) {
                    Log.e(TAG,"write: Error reading inputStream. "+e.getMessage());
                    break;
                }

            }
        }

        public void write(byte[] bytes){//chiamare questo metodo nella main activity per mandare dati ad un device remoto
            String text = new String(bytes, Charset.defaultCharset());
            Log.d(TAG,"write: Writing to outputstream: "+text);
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
                Log.e(TAG,"write: Error writing to outputstream. "+e.getMessage());
            }
        }

        public void cancel(){//chiamare questo metodo nella mainactivity per chiudere la connessione
            try{
                Log.d(TAG,"cancel: Closing Client Socket.");
                mmSocket.close();
            }catch (IOException e){
                Log.e(TAG,"cancel: close() of mmSocket in ConnectThread failed "+e.getMessage());
            }
        }

    }

    private void connected(BluetoothSocket mmSocket, BluetoothDevice mmDevice) {
        Log.d(TAG,"connected: Starting.");
        mConnectedThread = new ConnectedThread(mmSocket);
        mConnectThread.start();
    }

    public void write(byte[] out){
        Log.d(TAG,"write: Write Called");
        mConnectedThread.write(out);

    }

}
