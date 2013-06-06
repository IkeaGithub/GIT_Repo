package com.reg.atb.srv;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.Date;

import com.reg.atb.vo.IAtbDefine;
import com.reg.atb.utilo.AtbGlobals;
import com.reg.atb.utilo.AtbUtil;
import com.reg.atb.utilo.IAtbServerDefine;
import com.reg.atb.vo.AtbError;
import com.reg.atb.vo.AtbException;
import com.reg.atb.vo.AtbJob;
import com.reg.atb.vo.AtbQueueForTopic;
import com.reg.atb.vo.AtbQueueStatus;
import com.reg.core.util.AtbConstants;

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
public class AtbServerThread implements Runnable
{
  private Thread me = null;
  private Socket socket = null;
  protected InputStream is = null;
  protected OutputStream os = null;
  protected ObjectInputStream ois = null;
  protected ObjectOutputStream oos = null;
  private Object objIn = null;
  private AtbController atbController = null;
  private boolean threadDone = false;
  private String myName = "";
  private AtbServer atbServer = null;

  public AtbServerThread(AtbServer atbServer, Socket socket, int numThread) throws Exception
  {
    this.atbServer = atbServer;
    this.socket = socket;
    this.myName = "AtbServerThread|" + Integer.toString(socket.getPort()) + "|" + numThread;
    AtbJob ajIn = null;
    try
    {
      int soTimeout = AtbGlobals.getInstance().getAtbParameters().socketSoTimeout;
      this.socket.setSoTimeout(soTimeout);
      is = socket.getInputStream();
      os = socket.getOutputStream();
      ois = new ObjectInputStream(is);
      ajIn = (AtbJob) ois.readObject();
      oos = new ObjectOutputStream(os);
      oos.writeObject(ajIn);
      oos.flush();

      atbController = AtbGlobals.getInstance().getAtbController();

      me = new Thread(this, myName);
      me.setDaemon(true);
      debug("INIT_OK");
    }
    catch (ClassNotFoundException ex)
    {
      debug("INIT_NOT_OK_ClassNotFoundException");
      System.err.println("AtbServerThread 77 ClassNotFoundException at " + new Date());
      ex.printStackTrace();
      close();
      throw ex;
    }
    catch (SocketException ex)
    {
      debug("INIT_NOT_OK_SocketException");
      System.err.println("AtbServerThread 85 SocketException at " + new Date());
      ex.printStackTrace();
      close();
      throw ex;
    }
    catch (IOException ex)
    {
      debug("INIT_NOT_OK_IOException");
      System.err.println("AtbServerThread 93 IOException at " + new Date());
      ex.printStackTrace();
      close();
      throw ex;
    }
    catch (Exception ex)
    {
      debug("INIT_NOT_OK_Exception");
      System.err.println("AtbServerThread 101 Exception at " + new Date());
      ex.printStackTrace();
      close();
      throw ex;
    }
    finally
    {
      //
    }
  }

  public void startThread()
  {
    if (me != null)
    {
      debug("startThread");
      me.start();
    }
    else
    {
      error("startThread - NULL");
    }
  }

  public void done()
  {
    threadDone = true;
    close();
    debug("done");
    me = null;
  }

  public void run()
  {
    while (!threadDone)
    {
      AtbJob ajIn = null;
      AtbJob ajOut = null;
      String atbID = "";
      String batchID = "";
      try
      {
        // try to read an object from the in stream
        objIn = ois.readObject();
        if (objIn instanceof AtbJob)
        {
          ajIn = (AtbJob) objIn;
          if (!AtbGlobals.getInstance().getListening())
          {
            ajOut = new AtbJob();
            ajOut.actionId = IAtbDefine.FINISH_INDICATOR;
            writeOOS(ajOut);
            done();
          }
          switch (ajIn.actionId)
          {
            case IAtbServerDefine.CLOSE_ATB_INDICATOR:
              atbID = Integer.toString(ajIn.atbPrinter.id);
              System.out.println("----CLOSE_ATB_INDICATOR " + atbID + " " + ajIn.atbPrinter.name + " at " + new Date() + " socket " + socket);
              atbController.disconnectOneAtbPrinter(atbID);
              ajOut = new AtbJob();
              ajOut.actionId = IAtbDefine.FINISH_INDICATOR;
              writeOOS(ajOut);
              done();
              break;
            case IAtbDefine.REFRESH_PECTAB_INDICATOR:
              String pectabLetter = ajIn.atbPrinter.hostIpName;
              String pectabVersion = ajIn.atbPrinter.hostIpAddress;
              System.out.println("----REFRESH_PECTAB_INDICATOR - pectabLetter " + pectabLetter +
                                 " pectabVersion " + pectabVersion +
                                 " at " + new Date() + " socket " + socket);
              atbController.refreshPectab(pectabLetter, pectabVersion);
              ajOut = new AtbJob();
              ajOut.actionId = IAtbDefine.FINISH_INDICATOR;
              writeOOS(ajOut);
              done();
              break;
            case IAtbDefine.CHECK_CONNECTION_INDICATOR:
              atbID = Integer.toString(ajIn.atbPrinter.id);
              System.out.println("----CHECK_CONNECTION " + atbID + " " + ajIn.atbPrinter.name + " at " + new Date() + " socket " + socket);
              if (atbController.checkConnection(ajIn.atbPrinter))
              {
                ajOut = new AtbJob();
                ajOut.actionId = IAtbDefine.CHECK_CONNECTION_OK_INDICATOR;
                writeOOS(ajOut);
                System.out.println("     --CONNECTION_OK " + atbID + " " + ajIn.atbPrinter.name + " at " + new Date() + " socket " + socket);
              }
              else
              {
                ajOut = new AtbJob();
                ajOut.actionId = IAtbDefine.CHECK_CONNECTION_NON_OK_INDICATOR;
                writeOOS(ajOut);
                System.err.println("  -CONNECTION_NON_OK " + atbID + " " + ajIn.atbPrinter.name + " at " + new Date() + " socket " + socket);
              }
              break;
            case IAtbDefine.FINISH_INDICATOR:
              // client is the one managing the connection
              ajOut = new AtbJob();
              ajOut.actionId = IAtbDefine.FINISH_INDICATOR;
              writeOOS(ajOut);
              done();
              break;
            case IAtbDefine.STOP_SERVER_INDICATOR:
              AtbGlobals.getInstance().setListening(false);
              ajOut = new AtbJob();
              ajOut.actionId = IAtbDefine.FINISH_INDICATOR;
              writeOOS(ajOut);
              done();
              break;
            case IAtbDefine.REFRESH_INDICATOR:
              AtbQueueForTopic atbQ4T = atbController.getAllAtbPrintJob4Topic(ajIn.atbPrinter);
              int count = atbQ4T.atbPrintJobArr.length;
              atbQ4T.messageType = AtbConstants.ATB_STATUS_MESSAGE;
              for (int i = 0; i < count; i++)
              {
                if (atbQ4T.atbPrintJobArr[i].atbPrintJobStatus.status == AtbConstants.STATUS_IN_ERROR)
                {
                  AtbError atbError = atbController.getAtbError(ajIn.atbPrinter);
                  if ( (atbError != null) && (atbError.atbErrorId > 0))
                  {
                    atbQ4T.atbError = atbError;
                    atbQ4T.messageType = AtbConstants.ATB_ERROR_MESSAGE;
                  }
                  else
                  {
                    if (atbError == null)
                    {
                      error("run REFRESH_INDICATOR atbError is null STATUS_IN_ERROR for " + atbQ4T.atbPrintJobArr[i].batchId);
                    }
                    else
                    {
                      error("run REFRESH_INDICATOR atbError STATUS_IN_ERROR for[" +
                            atbQ4T.atbPrintJobArr[i].batchId +
                            "] atbErrorId[" + atbError.atbErrorId +
                            "] atbErrorDescription[" + atbError.atbErrorDescription + "]");
                    }
                  }
                  break;
                }
              }
              writeOOS(atbQ4T);
              break;
            case IAtbDefine.QUEUE_STATUS_INDICATOR:
              AtbQueueStatus aqs = atbController.getAtbQueueStatus(ajIn.atbPrinter);
              writeOOS(aqs);
              break;
            case IAtbDefine.POSTED_INDICATOR:
              atbController.postJob(ajIn.atbPrinter, ajIn.atbPrintJob);
              // we reply to say posted
              ajOut = new AtbJob();
              ajOut.actionId = IAtbDefine.POSTED_INDICATOR;
              writeOOS(ajOut);
              break;
            case IAtbDefine.PJ_CONFIRMED_INDICATOR:
            case IAtbDefine.PJ_ERROR_FIXED_INDICATOR:
            case IAtbDefine.PJ_MOVE_UP_INDICATOR:
            case IAtbDefine.PJ_MOVE_DOWN_INDICATOR:
            case IAtbDefine.PJ_QUEUE_IT_INDICATOR:
            case IAtbDefine.PJ_PAUSE_INDICATOR:
            case IAtbDefine.PJ_RETRY_INDICATOR:
            case IAtbDefine.PJ_REMOVE_INDICATOR:
              atbID = Integer.toString(ajIn.atbPrinter.id);
              batchID = Long.toString(ajIn.atbPrintJob.batchId);
              debug(atbID + "|" + batchID + "|" + AtbUtil.decodeAction(ajIn.actionId));
              atbController.manageRequest(ajIn.atbPrinter, ajIn.atbPrintJob);
              // we reply to say request posted
              ajOut = new AtbJob();
              ajOut.actionId = IAtbDefine.PJ_REQUEST_POSTED_INDICATOR;
              writeOOS(ajOut);
              break;
            default:
              writeOOS(ajIn);
              break;
          }
        } // end if instance of AtbJob
      }
      catch (AtbException ex)
      {
        String error = ex.getMessage();
        error("run AtbException " + error);
        if (ex.atbError.atbErrorId == 0)
        {
          ex.atbError = new AtbError(1, "APPLICATION GROUP ERROR", 1, "APPLICATION ERROR");
        }
        //2007-04-07 21:11:34,185 ERROR - AtbServerThread 1480|run AtbException IMPOSSIBLE TO GET ATB CONNECTION - TIMEOUT
        if (error.startsWith("IMPOSSIBLE TO GET ATB CONNECTION - TIMEOUT"))
        {
          done();
        }
        else
        {
          try
          {
            AtbQueueForTopic atbQ4T = new AtbQueueForTopic();
            atbQ4T.atbError = ex.atbError;
            atbQ4T.messageType = AtbConstants.ATB_ERROR_MESSAGE;
            writeOOS(atbQ4T);
          }
          catch (Exception exwo)
          {
            String err = "writeOOS Exception at " + (new Date()).toString();
            if (exwo.getMessage() != null)
            {
              err += " " + ex.getMessage();
            }
            System.out.println("AtbServerThread 306 " + err);
            done();
          }
        }
      }
      catch (IOException ex)
      {
        if (ex instanceof EOFException)
        {
          error("run EOFException");
        }
        else
        {
          System.out.println("AtbServerThread 319 IOException at " + new Date());
          ex.printStackTrace();
          error("run IOException " + ex.getMessage());
        }
        done();
      }
      catch (ClassNotFoundException ex)
      {
        System.out.println("AtbServerThread 327 ClassNotFoundException at " + new Date());
        ex.printStackTrace();
        error("run ClassNotFoundException " + ex.getMessage());
        done();
      }
    } // end while !threadDone
    //close();
  }

  private void writeOOS(Object obj) throws IOException
  {
    try
    {
      if (oos == null)
      {
        error("writeOOS oos NULL");
      }
      if (obj == null)
      {
        error("writeOOS obj NULL");
      }
      else if ( (oos != null) && (obj != null))
      {
        oos.writeObject(obj);
        oos.flush();
      }
    }
    catch (IOException ex)
    {
      System.out.println("AtbServerThread 356 IOException at " + new Date());
      ex.printStackTrace();
      throw ex;
    }
  }

  private void debug(String txt)
  {
    AtbUtil.logDebug(myName + "|" + txt);
  }

  private void error(String txt)
  {
    AtbUtil.logError(myName + "|" + txt);
  }

  private void close()
  {
    debug("close");
    try
    {
      if (oos != null)
      {
        oos.close();
        oos = null;
      }
      if (ois != null)
      {
        ois.close();
        ois = null;
      }
      if (os != null)
      {
        os.close();
        os = null;
      }
      if (is != null)
      {
        is.close();
        is = null;
      }
      if (socket != null)
      {
        atbServer.updateNbSocketClose();
        AtbUtil.closeSocket(myName, socket);
        socket = null;
      }
    }
    catch (IOException ex)
    {
      System.err.println("AtbServerThread 406 IOException at " + new Date());
      ex.printStackTrace();
    }
  }
}
