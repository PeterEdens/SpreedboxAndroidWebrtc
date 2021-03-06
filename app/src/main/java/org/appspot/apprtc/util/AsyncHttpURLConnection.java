/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.appspot.apprtc.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.text.TextUtils;

import org.apache.http.conn.ssl.StrictHostnameVerifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpCookie;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;



/**
 * Asynchronous http requests implementation.
 */
public class AsyncHttpURLConnection {
  private static final int HTTP_TIMEOUT_MS = 8000;
  private static final String HTTP_ORIGIN = "https://appr.tc";
  static final String COOKIES_HEADER = "Set-Cookie";
  private final String method;
  private final String url;
  private final String message;
  private final AsyncHttpEvents events;
  private String contentType;
  static java.net.CookieManager msCookieManager = new java.net.CookieManager();
  private String authorization = "";
  boolean mIsBitmap = false;

  public void setAuthorization(String message) {
    authorization = message;
  }

  public void setBitmap() {
    mIsBitmap = true;
  }

  /**
   * Http requests callbacks.
   */
  public interface AsyncHttpEvents {
    void onHttpError(String errorMessage);
    void onHttpComplete(String response);
    void onHttpComplete(Bitmap response);
  }

  public AsyncHttpURLConnection(String method, String url, String message, AsyncHttpEvents events) {
    this.method = method;
    this.url = url;
    this.message = message;
    this.events = events;
  }

  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  public void send() {
    Runnable runHttp = new Runnable() {
      public void run() {
        sendHttpMessage();
      }
    };
    new Thread(runHttp).start();
  }

  void getCookies(HttpsURLConnection connection) {

    Map<String, List<String>> headerFields = connection.getHeaderFields();
    if (headerFields != null) {
      List<String> cookiesHeader = headerFields.get(COOKIES_HEADER);

      if (cookiesHeader != null) {
        for (String cookie : cookiesHeader) {
          msCookieManager.getCookieStore().add(null, HttpCookie.parse(cookie).get(0));
        }
      }
    }
  }

  private void sendHttpMessage() {
    if (mIsBitmap) {
      Bitmap bitmap = ThumbnailsCacheManager.getBitmapFromDiskCache(url);

      if (bitmap != null) {
        events.onHttpComplete(bitmap);
        return;
      }
    }

    X509TrustManager trustManager = new X509TrustManager() {

      @Override
      public X509Certificate[] getAcceptedIssuers() {
        return null;
      }

      @Override
      public void checkServerTrusted(X509Certificate[] chain, String authType)
              throws CertificateException {
        // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        // NOTE : This is where we can calculate the certificate's fingerprint,
        // show it to the user and throw an exception in case he doesn't like it
      }

      @Override
      public void checkClientTrusted(X509Certificate[] chain, String authType)
              throws CertificateException {
      }
    };

    //HttpsURLConnection.setDefaultHostnameVerifier(new NullHostNameVerifier());
// Create a trust manager that does not validate certificate chains
    X509TrustManager[] trustAllCerts = new X509TrustManager[] { trustManager
    };

// Install the all-trusting trust manager
    SSLSocketFactory noSSLv3Factory = null;
    try {
      SSLContext sc = SSLContext.getInstance("TLS");
      sc.init(null, trustAllCerts, new java.security.SecureRandom());
      if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
        noSSLv3Factory = new TLSSocketFactory(trustAllCerts, new SecureRandom());
      } else {
        noSSLv3Factory = sc.getSocketFactory();
      }
      HttpsURLConnection.setDefaultSSLSocketFactory(noSSLv3Factory);
    } catch (GeneralSecurityException e) {
    }

    HttpsURLConnection connection = null;
    try {
      URL urlObj = new URL(url);
      connection = (HttpsURLConnection) urlObj.openConnection();
      connection.setSSLSocketFactory(noSSLv3Factory);

      HttpsURLConnection.setDefaultHostnameVerifier(new NullHostNameVerifier(urlObj.getHost()));
      connection.setHostnameVerifier(new NullHostNameVerifier(urlObj.getHost()));
      byte[] postData = new byte[0];
      if (message != null) {
        postData = message.getBytes("UTF-8");
      }

      if (msCookieManager.getCookieStore().getCookies().size() > 0) {
        // While joining the Cookies, use ',' or ';' as needed. Most of the servers are using ';'
        connection.setRequestProperty("Cookie",
                TextUtils.join(";",  msCookieManager.getCookieStore().getCookies()));
      }

      /*if (method.equals("PATCH")) {
        connection.setRequestProperty("X-HTTP-Method-Override", "PATCH");
        connection.setRequestMethod("POST");
      }
      else {*/
        connection.setRequestMethod(method);
      //}

      if (authorization.length() != 0) {
        connection.setRequestProperty("Authorization", authorization);
      }
      connection.setUseCaches(false);
      connection.setDoInput(true);
      connection.setConnectTimeout(HTTP_TIMEOUT_MS);
      connection.setReadTimeout(HTTP_TIMEOUT_MS);
      // TODO(glaznev) - query request origin from pref_room_server_url_key preferences.
      //connection.addRequestProperty("origin", HTTP_ORIGIN);
      boolean doOutput = false;
      if (method.equals("POST") || method.equals("PATCH")) {
        doOutput = true;
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(postData.length);
      }
      if (contentType == null) {
        connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
      } else {
        connection.setRequestProperty("Content-Type", contentType);
      }

      // Send POST request.
      if (doOutput && postData.length > 0) {
        OutputStream outStream = connection.getOutputStream();
        outStream.write(postData);
        outStream.close();
      }

      // Get response.
      int responseCode = 200;
      try {
        connection.getResponseCode();
      }
      catch (IOException e) {

      }
      getCookies(connection);
      InputStream responseStream;

      if (responseCode > 400) {
        responseStream = connection.getErrorStream();
      }
      else {
        responseStream = connection.getInputStream();
      }

      String responseType = connection.getContentType();
      if (responseType.startsWith("image/")) {
        Bitmap bitmap = BitmapFactory.decodeStream(responseStream);
        if (mIsBitmap && bitmap != null) {
          ThumbnailsCacheManager.addBitmapToCache(url, bitmap);
        }
        events.onHttpComplete(bitmap);
      }
      else {
        String response = drainStream(responseStream);
        events.onHttpComplete(response);
      }
      responseStream.close();
      connection.disconnect();
    } catch (SocketTimeoutException e) {
      events.onHttpError("HTTP " + method + " to " + url + " timeout");
    } catch (IOException e) {
      if (connection != null) {
        connection.disconnect();
      }
      events.onHttpError("HTTP " + method + " to " + url + " error: " + e.getMessage());
    }
    catch (ClassCastException e) {
      e.printStackTrace();
    }
  }

  // Return the contents of an InputStream as a String.
  private static String drainStream(InputStream in) {
    Scanner s = new Scanner(in).useDelimiter("\\A");
    return s.hasNext() ? s.next() : "";
  }
}
