package labelingStudy.nctu.minuku_2.manager;

import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import labelingStudy.nctu.minuku.Utilities.CSVHelper;
import labelingStudy.nctu.minuku_2.Utils;

/**
 * Created by Lawrence on 2018/10/22.
 */

public class PostManager extends AsyncTask<String, Void, String>{

    private final String TAG = "PostManager";

    public static final int HTTP_TIMEOUT = 10000; // millisecond
    public static final int SOCKET_TIMEOUT = 10000; // millisecond


    public PostManager(){}

    @Override
    protected void onPreExecute() {

    }

    @Override
    protected void onPostExecute(String result) {
        Log.d(TAG, "get http post result : " + result);
    }

    @Override
    protected String doInBackground(String... params) {

        String result = null;
        String url = params[0];
        String data = params[1];
        String dataType = params[2];
        String lastSyncTime = params[3];

        try {

            CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "going to send " + dataType + " data by postJSON, time : ", new JSONObject(data).getString("endTime"));
        }catch (JSONException e){

        }

        result = postJSON(url, data, dataType, lastSyncTime);

        CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "after sending " + dataType + " data by postJSON, result : ", result);

        return result;
    }

    public String postJSON(String address, String json, String dataType, String lastSyncTime) {

        Log.d(TAG, "[postJSON] testbackend post availSite to " + address);

        InputStream inputStream = null;
        String result = "";
        HttpURLConnection conn = null;
        try {

            URL url = new URL(address);
            conn = (HttpURLConnection) url.openConnection();
            Log.d(TAG, "[postJSON] testbackend connecting to " + address);

            if (url.getProtocol().toLowerCase().equals("https")) {
                Log.d(TAG, "[postJSON] [using https]");
                trustAllHosts();
                HttpsURLConnection https = (HttpsURLConnection) url.openConnection();
                https.setHostnameVerifier(DO_NOT_VERIFY);
                conn = https;
            } else {
                conn = (HttpURLConnection) url.openConnection();
            }

            SSLContext sc;
            sc = SSLContext.getInstance("TLS");
            sc.init(null, null, new java.security.SecureRandom());

            //TODO testing to solve the SocketTimeoutException issue
            conn.setReadTimeout(HTTP_TIMEOUT);
            conn.setConnectTimeout(SOCKET_TIMEOUT);

            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);
            //TODO might need to use long instead of int is for the larger size but restricted to the api level should over 19
            conn.setFixedLengthStreamingMode(json.getBytes().length);
            conn.setRequestProperty("Content-Type","application/json");
            conn.connect();

            OutputStreamWriter wr= new OutputStreamWriter(conn.getOutputStream());
            wr.write(json);
            wr.close();

            Log.d(TAG, "Post:\t" + dataType + "\t" + "for lastSyncTime:" + lastSyncTime);

            int responseCode = conn.getResponseCode();

            if(responseCode != HttpsURLConnection.HTTP_OK){

                CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "fail to connect to the server, error code: "+responseCode);
                CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "going to throw IOException");

                throw new IOException("HTTP error code: " + responseCode);
            } else {

                CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "connected to the server successfully");

                inputStream = conn.getInputStream();
            }
            result = convertInputStreamToString(inputStream);

            Log.d(TAG, "[postJSON] the result response code is " + responseCode);
            Log.d(TAG, "[postJSON] the result is " + result);

        } catch (NoSuchAlgorithmException e) {

            Log.d(TAG, "NoSuchAlgorithmException", e);
            CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "NoSuchAlgorithmException", Utils.getStackTrace(e));
        } catch (KeyManagementException e) {
            e.printStackTrace();
            Log.d(TAG, "KeyManagementException", e);
            CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "KeyManagementException", Utils.getStackTrace(e));
        } catch (ProtocolException e) {
            Log.d(TAG, "ProtocolException", e);
            CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "ProtocolException", Utils.getStackTrace(e));
        } catch (MalformedURLException e) {

            Log.d(TAG, "MalformedURLException", e);
            CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "MalformedURLException", Utils.getStackTrace(e));
        } catch (java.net.SocketTimeoutException e){

            Log.d(TAG, "SocketTimeoutException EE", e);
            CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "SocketTimeoutException", Utils.getStackTrace(e));
        } catch (IOException e) {
            e.printStackTrace();

            CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "IOException", Utils.getStackTrace(e));
        }finally {

            CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "connection is null ? "+(conn != null));

            if (conn != null) {

                CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "going to disconnect");

                conn.disconnect();

                CSVHelper.storeToCSV(CSVHelper.CSV_Wifi, "disconnected successfully");
            }
        }

        return result;
    }

    private void trustAllHosts() {

        X509TrustManager easyTrustManager = new X509TrustManager() {

            public void checkClientTrusted(
                    X509Certificate[] chain,
                    String authType) throws CertificateException {
            }

            public void checkServerTrusted(
                    X509Certificate[] chain,
                    String authType) throws CertificateException {
            }

            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }

        };

        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[] {easyTrustManager};

        // Install the all-trusting trust manager
        try {
            SSLContext sc = SSLContext.getInstance("TLS");

            sc.init(null, trustAllCerts, new java.security.SecureRandom());

            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String convertInputStreamToString(InputStream inputStream) throws IOException{

        BufferedReader bufferedReader = new BufferedReader( new InputStreamReader(inputStream));
        String line = "";
        String result = "";
        while((line = bufferedReader.readLine()) != null){
//            Log.d(LOG_TAG, "[syncWithRemoteDatabase] " + line);
            result += line;
        }

        inputStream.close();
        return result;

    }

    public HostnameVerifier DO_NOT_VERIFY = new HostnameVerifier() {

        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    };
}
