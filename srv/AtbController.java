package com.reg.atb.srv;

import java.util.Date;

import com.reg.atb.conn.AtbDialog;
import com.reg.atb.conn.AtbDialogPool;
import com.reg.atb.conn.RefreshThread;
import com.reg.atb.utilo.AtbUtil;
import com.reg.atb.utilo.IAtbServerDefine;
import com.reg.atb.vo.AtbError;
import com.reg.atb.vo.AtbException;
import com.reg.atb.vo.AtbPrintJob;
import com.reg.atb.vo.AtbPrinter;
import com.reg.atb.vo.AtbQueueForTopic;
import com.reg.atb.vo.AtbQueueStatus;

public class AtbController
{
  public AtbController()
  {
    try
    {
      startRefreshThread();
    }
    catch (Exception ex)
    {
      System.out.println("AtbController 27 Exception at " + new Date());
      ex.printStackTrace();
    }
  }

  private void startRefreshThread() throws Exception
  {
    RefreshThread refreshThread = new RefreshThread();
    refreshThread.startThread();
  }

  public void postJob(AtbPrinter atbPrinter, AtbPrintJob atbPrintJob) throws AtbException
  {
    AtbDialog atbDialog = getAtbDialog(atbPrinter);
    atbDialog.postAtbPrintJob(atbPrintJob);
  }

  public void manageRequest(AtbPrinter atbPrinter, AtbPrintJob atbPrintJob) throws AtbException
  {
    AtbDialog atbDialog = getAtbDialog(atbPrinter);
    atbDialog.manageRequest(atbPrintJob);
  }

  public AtbQueueStatus getAtbQueueStatus(AtbPrinter atbPrinter) throws AtbException
  {
    AtbDialog atbDialog = getAtbDialog(atbPrinter);
    return atbDialog.getAtbQueueStatus();
  }

  public AtbQueueForTopic getAllAtbPrintJob4Topic(AtbPrinter atbPrinter) throws AtbException
  {
    AtbDialog atbDialog = getAtbDialog(atbPrinter);
    return atbDialog.getAllAtbPrintJob4Topic();
  }

  public AtbError getAtbError(AtbPrinter atbPrinter) throws AtbException
  {
    AtbDialog atbDialog = getAtbDialog(atbPrinter);
    return atbDialog.getAtbError();
  }

  public void disconnectAllAtbPrinter() throws AtbException
  {
    AtbDialogPool.disconnectAllAtbPrinter();
  }

  public void refreshPectab(String pectabLetter, String pectabVersion) throws AtbException
  {
    AtbDialogPool.refreshPectab(pectabLetter, pectabVersion);
  }

  public void disconnectOneAtbPrinter(String atbID) throws AtbException
  {
    AtbDialogPool.disconnectOneAtbPrinter(atbID);
  }

  public boolean checkConnection(AtbPrinter atbPrinter) throws AtbException
  {
    AtbDialog atbDialog = null;
    boolean status = false;
    try
    {
      atbDialog = getAtbDialog(atbPrinter);
      if (atbDialog == null)
      {
        AtbError atbErr = new AtbError();
        atbErr.atbErrorGroupId = 2;
        atbErr.atbErrorGroupDescription = IAtbServerDefine.ATB_ERR_GROUP[atbErr.atbErrorGroupId];
        atbErr.atbErrorId = 98;
        atbErr.atbErrorDescription = "INTERNAL PROGRAM ERROR AtbController line 96";
        throw new AtbException("ATB DIALOG IS NULL", atbErr);
      }
      atbDialog.checkAtbCon();
      atbDialog.setLastAccessTime(System.currentTimeMillis());
      status = true;
    }
    catch (AtbException ex)
    {
      String atbID = Integer.toString(atbPrinter.id) + " " + atbPrinter.name;
      AtbUtil.logError(atbID + " -> checkConnection AtbException " + ex.getMessage());
      status = false;
    }
    finally
    {
      atbDialog = null;
    }
    return status;
  }

  private static AtbDialog getAtbDialog(AtbPrinter atbPrinter) throws AtbException
  {
    AtbDialog atbDialog = null;
    try
    {
      atbDialog = AtbDialogPool.getAtbDialog(atbPrinter, false);
      if (atbDialog == null)
      {
        AtbError atbErr = new AtbError();
        atbErr.atbErrorGroupId = 2;
        atbErr.atbErrorGroupDescription = IAtbServerDefine.ATB_ERR_GROUP[atbErr.atbErrorGroupId];
        atbErr.atbErrorId = 98;
        atbErr.atbErrorDescription = "INTERNAL PROGRAM ERROR AtbController line 128";
        throw new AtbException("ATB DIALOG IS NULL", atbErr);
      }
    }
    catch (AtbException ex)
    {
      System.out.println("AtbController 134 AtbException at " + new Date());
      throw ex;
    }
    return atbDialog;
  }
}
