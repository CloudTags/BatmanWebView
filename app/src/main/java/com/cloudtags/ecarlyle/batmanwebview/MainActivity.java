package com.cloudtags.ecarlyle.batmanwebview;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity {
    public ArrayList<Integer> assigned_freqs = new ArrayList<Integer>();
    int frequency = 48000;
    int channelConfiguration = AudioFormat.CHANNEL_IN_MONO;
    int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
    AudioRecord audioRecord;

    private RealDoubleFFT transformer;
    int blockSize = 2048;
    boolean started = false;
    boolean CANCELLED_FLAG = false;
    int width;
    int height;
    public STFT stft;
    public HashMap<Integer, Double> masterfound_freq;
    RecordAudio recordTask;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        WebView myWebView = (WebView) findViewById(R.id.webview);
        WebSettings webSettings = myWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        myWebView.addJavascriptInterface(new WebAppInterface(this), "android");
        //assigned_freqs.add(1000);
        assigned_freqs.add(18500);
        myWebView.loadUrl("http://peitho.cloudtags.com:3000/Batman?client=MediaMarkt_0&tapwall=1&Row=5&Column=3");
    }

    public class WebAppInterface {
        Context mContext;

        /** Instantiate the interface and set the context */
        WebAppInterface(Context c) {
            mContext = c;
        }

        /** Show a toast from the web page */
        @android.webkit.JavascriptInterface
        public void showToast(String toast) {
            Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show();
        }

        @android.webkit.JavascriptInterface
        public int addToAssigned_freqs(Integer newfreq){

            if(assigned_freqs.contains(newfreq)){
                return 1;
            } else {
                assigned_freqs.add(newfreq);
                return 1;
            }
        }

        @android.webkit.JavascriptInterface
        public int removeAssigned_freqs(Integer delfreq){

            if(assigned_freqs.contains(delfreq)){
                assigned_freqs.remove(delfreq);
            }
            return 1;
        }

        @android.webkit.JavascriptInterface
        public int Microphone_StartStop( ){
            if(started){
                started=false;
                Log.d("Status", "Microphone Stopped by click");
                audioRecord.stop();
                recordTask.cancel(true);
                return 0;
            } else {
                started = true;
                Log.d("Status", "Microphone Started by click ");
                recordTask = new RecordAudio();
                recordTask.execute();
                return 1;
            }
        }

        @android.webkit.JavascriptInterface
        public int Microphone_Status( ){
            if(started  ){
                return 0;
            } else {

                Integer d = (Integer) stft.foundfreq.keySet().toArray()[0];
                return  d;
            }

        }



    }

    private class RecordAudio extends AsyncTask<Void, Integer, Boolean> {
        DoubleSineGen sineGen1;
        DoubleSineGen sineGen2;
        double[] mdata;
        private final static double SAMPLE_VALUE_MAX = 32767.0;
        private double baseTimeMs = SystemClock.uptimeMillis();
        double maxAmpDB;
        double maxAmpFreq;
        int dblevel = -40;

        @Override
        protected Boolean doInBackground(Void... params) {

            int BYTE_OF_SAMPLE = 2;
            int bufferSize = AudioRecord.getMinBufferSize(frequency,
                    channelConfiguration, audioEncoding);
            String wndFuncName = "Hanning";


            int readChunkSize    = blockSize/2;  // /2 due to overlapped analyze window
            readChunkSize        = Math.min(readChunkSize, 2048);  // read in a smaller chunk, hopefully smaller delay
            int bufferSampleSize = Math.max(bufferSize / BYTE_OF_SAMPLE, blockSize/2) * 2;
            // tolerate up to about 1 sec.
            bufferSampleSize = (int)Math.ceil(1.0 * frequency / bufferSampleSize) * bufferSampleSize;

            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.DEFAULT, frequency,
                    channelConfiguration, audioEncoding, BYTE_OF_SAMPLE * bufferSampleSize);
            //Log.d("bufferSize:", Integer.toString(BYTE_OF_SAMPLE * bufferSampleSize));
            int bufferReadResult;
            short[] buffer = new short[readChunkSize];
            double[] toTransform = new double[readChunkSize];

            stft = new STFT(blockSize, frequency, wndFuncName);

            try {
                audioRecord.startRecording();
            } catch (IllegalStateException e) {
                Log.e("Recording failed", e.toString());

            }
            while (started) {

                if (isCancelled() || (CANCELLED_FLAG == true)) {

                    started = false;
                    //publishProgress(cancelledResult);
                    Log.d("doInBackground", "Cancelling the RecordTask");
                    break;
                } else {
                    bufferReadResult = audioRecord.read(buffer, 0, readChunkSize);
                    int numOfReadShort;
                    numOfReadShort = readTestData(buffer, 0, readChunkSize, MediaRecorder.AudioSource.DEFAULT);
                    stft.feedData(buffer, numOfReadShort);

                    //stft.calculatePeak();
                    //maxAmpFreq = stft.maxAmpFreq;
                    //maxAmpDB = stft.maxAmpDB;
                    //Log.d("MaxFreq", Double.toString(maxAmpFreq));
                    //Log.d("Dbs", Double.toString(maxAmpDB));
                    if(assigned_freqs.size() > 0 ){
                        stft.findAssignedFrequencies(assigned_freqs, dblevel);

                    }



                    publishProgress(1);

                }

            }
            return true;
        }
        @Override
        protected void onProgressUpdate(Integer...progress) {
            if(stft.foundfreq.size()>0) {
//                TODO: Look for largest value//

                started=false;
                Log.d("Status", "Microphone Stopped by found frequency");
                audioRecord.stop();
                recordTask.cancel(true);

//                TODO: Send Result directly from here?
               // startstopButton.setText("Start");
                JSONObject FrequencyFound = new JSONObject();




                //TextView fTextView = (TextView) findViewById(R.id.foundfreqtext);
                //fTextView.setText(android.text.TextUtils.join(",", stft.foundfreq.keySet()));


            }

            //Log.e("RecordingProgress", "Displaying in progress");

            //Log.d("Test:", Integer.toString(progress[0].length));

            /*for (int i = 0; i < progress[0].length; i++) {
                int x = 2 * i;
                int downy = (int) (150 - (progress[0][i] * 10));
                int upy = 150;
                Log.d("ToLine", "ToLine");
               // canvasDisplaySpectrum.drawLine(x, downy, x, upy, paintSpectrumDisplay);
            }
            */

        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            try {
                audioRecord.stop();
            }
            catch(IllegalStateException e){
                Log.e("Stop failed", e.toString());

            }
        }

        private int readTestData(short[] a, int offsetInShorts, int sizeInShorts, int id) {
            if (mdata == null || mdata.length != sizeInShorts) {
                mdata = new double[sizeInShorts];
            }
            Arrays.fill(mdata, 0.0);
            switch (id - 1000) {
                case 1:
                    sineGen2.getSamples(mdata);
                case 0:
                    sineGen1.addSamples(mdata);
                    for (int i = 0; i < sizeInShorts; i++) {
                        a[offsetInShorts + i] = (short) Math.round(mdata[i]);
                    }
                    break;
                case 2:
                    for (int i = 0; i < sizeInShorts; i++) {
                        a[i] = (short) (SAMPLE_VALUE_MAX * (2.0*Math.random() - 1));
                    }
                    break;
                default:
                    //Log.d("d", "readTestData(): No this source id = " + MediaRecorder.AudioSource.DEFAULT);
            }
            // LimitFrameRate(1000.0*sizeInShorts / frequency);
            return sizeInShorts;
        }

        private void LimitFrameRate(double updateMs) {
            // Limit the frame rate by wait `delay' ms.
            baseTimeMs += updateMs;
            long delay = (int) (baseTimeMs - SystemClock.uptimeMillis());
//      Log.i(TAG, "delay = " + delay);
            if (delay > 0) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Log.i("dda", "Sleep interrupted");  // seems never reached
                }
            } else {
                baseTimeMs -= delay;  // get current time
                // Log.i(TAG, "time: cmp t="+Long.toString(SystemClock.uptimeMillis())
                //            + " v.s. t'=" + Long.toString(baseTimeMs));
            }
        }

        protected void onCancelled(Boolean result){

            try{
                audioRecord.stop();
            }
            catch(IllegalStateException e){
                Log.e("Stop failed", e.toString());

            }
           /* //recordTask.cancel(true);
            Log.d("FFTSpectrumAnalyzer","onCancelled: New Screen");
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
*/
        }




    }

}
