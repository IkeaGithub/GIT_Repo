package com.reg.atb.srv;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;

import com.reg.atb.utilo.AtbGlobals;
import com.reg.atb.utilo.AtbUtil;
import com.reg.atb.utilo.IAtbServerDefine;
import com.reg.atb.vo.AtbJob;
import com.reg.core.util.REGUtil;

/**
 * <p>Title: ATB server</p>
 *
 * <p>Description: Socket server for ATB connections</p>
 *
 * <p>Copyright: Copyright (c) 2006</p>
 *
 * <p>Company: REG IT</p>
 *
 * @author Christian Lambert
 * @version 1.0
 */
public class AtbServer
{
  private ServerSocket serverSocket = null;
  private int nbSocketOpen = 0;
  private int nbSocketClose = 0;
  private int numThread = 0;
  private int listeningPort = 17777;

  public AtbServer()
  {
    listeningPort = AtbGlobals.getInstance().getListeningPort();
    try
    {
      int listeningPort = AtbGlobals.getInstance().getListeningPort();
      int backlog = AtbGlobals.getInstance().getBacklog();
      serverSocket = new ServerSocket(listeningPort, backlog);
      System.out.println("AtbServer - listening on port: " + listeningPort);
      System.out.println("AtbServer - SoTimeout        : " + serverSocket.getSoTimeout());
      System.out.println("AtbServer - backlog          : " + backlog);

      //  IOException: Broken pipe
      //
      // Once an application has performed network access (i.e. urlconnection, parsing of xml document with external references, etc),
      // the DNS settings get cached so any subsequent operation will use the old settings even if the real settings have changed.
      // To reset everything, you have to restart the server since the the default setting JVM setting is to cache forever.
      // There are 4 properties that can be used to override the default behaviour.
      //
      // networkaddress.cache.ttl (default: -1)
      //     Specified in java.security to indicate the caching policy for successful
      //     name lookups from the name service. The value is specified as as integer
      //     to indicate the number of seconds to cache the successful lookup.
      //
      //     A value of -1 indicates "cache forever".
      //
      // networkaddress.cache.negative.ttl (default: 10)
      //     Specified in java.security to indicate the caching policy for un-successful
      //     name lookups from the name service. The value is specified as as integer to
      //     indicate the number of seconds to cache the failure for un-successful lookups.
      //
      //     A value of 0 indicates "never cache". A value of -1 indicates "cache forever".
      //
      // sun.net.inetaddr.ttl
      //     This is a sun private system property which corresponds to networkaddress.cache.ttl.
      //     It takes the same value and has the same meaning, but can be set as a command-line
      //     option. However, the preferred way is to use the security property mentioned above.
      //
      // sun.net.inetaddr.negative.ttl
      //     This is a sun private system property which corresponds to networkaddress.cache.negative.ttl.
      //     It takes the same value and has the same meaning, but can be set as a command-line option.
      //     However, the preferred way is to use the security property mentioned above.
      //
      // So you can disable caching by adding -Dsun.net.inetaddr.ttl=0 on the command line starting the JVM.
      // But you can't set the value of networkaddress.cache.ttl on the command line.
      // You can set the required value in the java.security file located in %JRE%\lib\security
      // networkaddress.cache.ttl=60
      // networkaddress.cache.negative.ttl=10
      //
      // or set the value in your code with
      // java.security.Security.setProperty("networkaddress.cache.ttl" , "0");
      java.security.Security.setProperty("networkaddress.cache.ttl", "0");

      startListening();
    }
    catch (IOException ex)
    {
      ex.printStackTrace();
      System.exit( -1);
    }
  }

  public void updateNbSocketClose()
  {
    this.nbSocketClose++;
  }

  private void startListening() //throws IOException
  {
    boolean listening = AtbGlobals.getInstance().getListening();
    boolean acceptOK = false;
    Socket socket = null;
    while (listening)
    {
      try
      {
        acceptOK = false;
        socket = serverSocket.accept();
        acceptOK = true;
        if (numThread > IAtbServerDefine.MAX_VALUE)
        {
          numThread = 0;
        }
        numThread++;
        nbSocketOpen++;
        AtbUtil.logDebug("AtbServer|new socket|" + numThread + "|" + nbSocketOpen + "|" + nbSocketClose + "|" + socket.toString());
        //let's check if server was asked To Stop
        listening = AtbGlobals.getInstance().getListening();
        if (listening)
        {
          AtbServerThread ast = new AtbServerThread(this, socket, numThread);
          ast.startThread();
        }
        else
        {
          InputStream is = socket.getInputStream();
          OutputStream os = socket.getOutputStream();
          ObjectInputStream ois = new ObjectInputStream(is);
          ObjectOutputStream oos = new ObjectOutputStream(os);
          AtbJob ajIn = (AtbJob) ois.readObject();
          System.out.println("AtbServer listening FALSE ajOut: " + AtbUtil.decodeAction(ajIn.actionId));
          oos.writeObject(ajIn);
          oos.flush();
          ois.close();
          oos.close();
          is.close();
          os.close();
          AtbUtil.closeSocket("AtbServer", socket);
          updateNbSocketClose();
        }
      }
      catch (Exception ex)
      {
        /**
         * @exception  IOException  if an I/O error occurs when waiting for a
         *               connection.
         * @exception  SecurityException  if a security manager exists and its
         *             <code>checkListen</code> method doesn't allow the operation.
         * @exception  SocketTimeoutException if a timeout was previously set with setSoTimeout and
         *             the timeout has been reached.
         * @exception  java.nio.channels.IllegalBlockingModeException
         *             if this socket has an associated channel,
         *             and the channel is in non-blocking mode.
         */
        AtbUtil.closeSocket("AtbServer", socket);
        socket = null;
        updateNbSocketClose();
        // send an email
        String subject = REGUtil.getHostName() + " - AtbServer Exception";
        String msg = "Exception:\n";
        String now = (new Date()).toString();
        msg = "acceptOK -> " + acceptOK + " at " + now;
        if (ex.getMessage() != null)
        {
          msg += " Exception " + ex.getMessage();
        }
        AtbUtil.sendCriticalEmail(msg, subject);
        System.err.println("AtbServer 175 Exception " + msg);
        ex.printStackTrace();
      }
    } // end while
    exitApp();
  }

  private void exitApp()
  {
    String subject = REGUtil.getHostName() + " - AtbServer STOPPING";
    String msg = "Listening STOPPING. ServerSocket closing";
    AtbUtil.sendCriticalEmail(msg, subject);
    try
    {
      // Disconnect All AtbPrinter
      AtbController atbController = AtbGlobals.getInstance().getAtbController();
      atbController.disconnectAllAtbPrinter();
      AtbUtil.logDebug("AtbServer|exitApp|nbSocketOpen " + nbSocketOpen + "|nbSocketClose " + nbSocketClose);
    }
    catch (Exception ex)
    {
      ex.printStackTrace();
    }
    // Close ServerSocket
    try
    {
      serverSocket.close();
    }
    catch (IOException ex1)
    {
      ex1.printStackTrace();
    }
    System.exit(0);
  }

  public static void main(String[] args)
  {
    AtbServer as = new AtbServer();
  }
}
