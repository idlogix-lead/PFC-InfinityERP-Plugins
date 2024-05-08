package org.solution.callouts;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Properties;
import java.util.logging.Level;
import org.adempiere.base.Core;
import org.adempiere.base.IProductPricing;
import org.adempiere.model.GridTabWrapper;
import org.compiere.model.CalloutEngine;
import org.compiere.model.GridField;
import org.compiere.model.GridTab;
import org.compiere.model.I_C_OrderLine;
import org.compiere.model.MBPartnerLocation;
import org.compiere.model.MOrderLine;
import org.compiere.model.MOrgInfo;
import org.compiere.model.MPaymentTerm;
import org.compiere.model.MPriceList;
import org.compiere.model.MPriceListVersion;
import org.compiere.model.MProduct;
import org.compiere.model.MRole;
import org.compiere.model.MSequence;
import org.compiere.model.MStorageReservation;
import org.compiere.model.MUOM;
import org.compiere.model.MUOMConversion;
import org.compiere.model.MWarehouse;
import org.compiere.model.Tax;
import org.compiere.model.X_C_POSTenderType;
import org.compiere.util.CLogger;
import org.compiere.util.CPreparedStatement;
import org.compiere.util.DB;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;
import org.compiere.util.Msg;

public class CalloutOrder extends CalloutEngine {
  private boolean steps = false;
  
  public String docType(Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value) {
    CPreparedStatement cPreparedStatement = null;
    Integer C_DocType_ID = (Integer)value;
    if (C_DocType_ID == null || C_DocType_ID.intValue() == 0)
      return ""; 
    String oldDocNo = (String)mTab.getValue("DocumentNo");
    boolean newDocNo = (oldDocNo == null);
    if (!newDocNo && oldDocNo.startsWith("<") && oldDocNo.endsWith(">"))
      newDocNo = true; 
    Integer oldC_DocType_ID = (Integer)mTab.getValue("C_DocType_ID");
    String sql = "SELECT d.DocSubTypeSO,d.HasCharges,d.IsDocNoControlled,s.AD_Sequence_ID,d.IsSOTrx FROM C_DocType d LEFT OUTER JOIN AD_Sequence s ON (d.DocNoSequence_ID=s.AD_Sequence_ID) WHERE C_DocType_ID=?";
    PreparedStatement pstmt = null;
    ResultSet rs = null;
    try {
      int oldAD_Sequence_ID = 0;
      if (!newDocNo && oldC_DocType_ID != null && oldC_DocType_ID.intValue() != 0) {
        CPreparedStatement cPreparedStatement1 = DB.prepareStatement(sql, null);
        cPreparedStatement1.setInt(1, oldC_DocType_ID.intValue());
        rs = cPreparedStatement1.executeQuery();
        if (rs.next())
          oldAD_Sequence_ID = rs.getInt("AD_Sequence_ID"); 
        DB.close(rs, (Statement)cPreparedStatement1);
        rs = null;
        cPreparedStatement1 = null;
      } 
      cPreparedStatement = DB.prepareStatement(sql, null);
      cPreparedStatement.setInt(1, C_DocType_ID.intValue());
      rs = cPreparedStatement.executeQuery();
      String DocSubTypeSO = "";
      boolean IsSOTrx = true;
      if (rs.next()) {
        DocSubTypeSO = rs.getString("DocSubTypeSO");
        if (DocSubTypeSO == null)
          DocSubTypeSO = "--"; 
        Env.setContext(ctx, WindowNo, "OrderType", DocSubTypeSO);
        if (!DocSubTypeSO.equals("SO"))
          mTab.setValue("IsDropShip", "N"); 
        if (DocSubTypeSO.equals("WR")) {
          mTab.setValue("DeliveryRule", "F");
        } else if (DocSubTypeSO.equals("PR")) {
          mTab.setValue("DeliveryRule", "R");
        } else {
          mTab.setValue("DeliveryRule", "A");
        } 
        if (DocSubTypeSO.equals("WR") || 
          DocSubTypeSO.equals("PR") || 
          DocSubTypeSO.equals("WI")) {
          mTab.setValue("InvoiceRule", "I");
        } else {
          mTab.setValue("InvoiceRule", "D");
        } 
        if (DocSubTypeSO.equals("WR")) {
          mTab.setValue("PaymentRule", "B");
        } else {
          mTab.setValue("PaymentRule", "P");
        } 
        if ("N".equals(rs.getString("IsSOTrx")))
          IsSOTrx = false; 
        Env.setContext(ctx, WindowNo, "HasCharges", rs.getString("HasCharges"));
        if (rs.getString("IsDocNoControlled").equals("Y")) {
          if (!newDocNo && oldAD_Sequence_ID != rs.getInt("AD_Sequence_ID"))
            newDocNo = true; 
          if (newDocNo) {
            int AD_Sequence_ID = rs.getInt("AD_Sequence_ID");
            mTab.setValue("DocumentNo", MSequence.getPreliminaryNo(mTab, AD_Sequence_ID));
          } 
        } 
      } 
      DB.close(rs, (Statement)cPreparedStatement);
      rs = null;
      cPreparedStatement = null;
      if (!DocSubTypeSO.equals("WR") && 
        !DocSubTypeSO.equals("PR")) {
        sql = "SELECT PaymentRule,C_PaymentTerm_ID,InvoiceRule,DeliveryRule,FreightCostRule,DeliveryViaRule, PaymentRulePO,PO_PaymentTerm_ID FROM C_BPartner WHERE C_BPartner_ID=?";
        cPreparedStatement = DB.prepareStatement(sql, null);
        int C_BPartner_ID = Env.getContextAsInt(ctx, WindowNo, "C_BPartner_ID");
        cPreparedStatement.setInt(1, C_BPartner_ID);
        rs = cPreparedStatement.executeQuery();
        if (rs.next()) {
          String s = rs.getString(IsSOTrx ? "PaymentRule" : "PaymentRulePO");
          if (s != null && s.length() != 0)
            mTab.setValue("PaymentRule", s); 
          Integer ii = Integer.valueOf(rs.getInt(IsSOTrx ? "C_PaymentTerm_ID" : "PO_PaymentTerm_ID"));
          if (!rs.wasNull())
            mTab.setValue("C_PaymentTerm_ID", ii); 
          s = rs.getString(3);
          if (s != null && s.length() != 0)
            mTab.setValue("InvoiceRule", s); 
          s = rs.getString(4);
          if (s != null && s.length() != 0)
            mTab.setValue("DeliveryRule", s); 
          s = rs.getString(5);
          if (s != null && s.length() != 0)
            mTab.setValue("FreightCostRule", s); 
          s = rs.getString(6);
          if (s != null && s.length() != 0)
            mTab.setValue("DeliveryViaRule", s); 
        } 
      } 
    } catch (SQLException e) {
      this.log.log(Level.SEVERE, sql, e);
      return e.getLocalizedMessage();
    } finally {
      DB.close(rs, (Statement)cPreparedStatement);
      rs = null;
      cPreparedStatement = null;
    } 
    return "";
  }
  
  public String bPartner(Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value) {
    CPreparedStatement cPreparedStatement = null;
    Integer C_BPartner_ID = (Integer)value;
    if (C_BPartner_ID == null || C_BPartner_ID.intValue() == 0)
      return ""; 
    String sql = "SELECT p.AD_Language,p.C_PaymentTerm_ID, COALESCE(p.M_PriceList_ID,g.M_PriceList_ID) AS M_PriceList_ID, p.PaymentRule,p.POReference, p.SO_Description,p.IsDiscountPrinted, p.InvoiceRule,p.DeliveryRule,p.FreightCostRule,DeliveryViaRule, p.SO_CreditLimit, p.SO_CreditLimit-p.SO_CreditUsed AS CreditAvailable, (select max(lship.C_BPartner_Location_ID) from C_BPartner_Location lship where p.C_BPartner_ID=lship.C_BPartner_ID AND lship.IsShipTo='Y' AND lship.IsActive='Y') as C_BPartner_Location_ID, (select max(c.AD_User_ID) from AD_User c where p.C_BPartner_ID=c.C_BPartner_ID AND c.IsActive='Y') as AD_User_ID, COALESCE(p.PO_PriceList_ID,g.PO_PriceList_ID) AS PO_PriceList_ID, p.PaymentRulePO,p.PO_PaymentTerm_ID, (select max(lbill.C_BPartner_Location_ID) from C_BPartner_Location lbill where p.C_BPartner_ID=lbill.C_BPartner_ID AND lbill.IsBillTo='Y' AND lbill.IsActive='Y') AS Bill_Location_ID,  p.SOCreditStatus,  p.SalesRep_ID FROM C_BPartner p INNER JOIN C_BP_Group g ON (p.C_BP_Group_ID=g.C_BP_Group_ID)WHERE p.C_BPartner_ID=? AND p.IsActive='Y'";
    boolean IsSOTrx = "Y".equals(Env.getContext(ctx, WindowNo, "IsSOTrx"));
    PreparedStatement pstmt = null;
    ResultSet rs = null;
    try {
      cPreparedStatement = DB.prepareStatement(sql, null);
      cPreparedStatement.setInt(1, C_BPartner_ID.intValue());
      rs = cPreparedStatement.executeQuery();
      if (rs.next()) {
        Integer salesRep = Integer.valueOf(rs.getInt("SalesRep_ID"));
        if (IsSOTrx && salesRep.intValue() != 0)
          mTab.setValue("SalesRep_ID", salesRep); 
        Integer ii = Integer.valueOf(rs.getInt(IsSOTrx ? "M_PriceList_ID" : "PO_PriceList_ID"));
        if (!rs.wasNull()) {
          mTab.setValue("M_PriceList_ID", ii);
        } else {
          int i = Env.getContextAsInt(ctx, "#M_PriceList_ID");
          if (i != 0) {
            MPriceList pl = new MPriceList(ctx, i, null);
            if (IsSOTrx == pl.isSOPriceList()) {
              mTab.setValue("M_PriceList_ID", Integer.valueOf(i));
            } else {
              String sql2 = "SELECT M_PriceList_ID FROM M_PriceList WHERE AD_Client_ID=? AND IsSOPriceList=? AND IsActive='Y' ORDER BY IsDefault DESC";
              ii = Integer.valueOf(DB.getSQLValue(null, sql2, new Object[] { Integer.valueOf(Env.getAD_Client_ID(ctx)), Boolean.valueOf(IsSOTrx) }));
              if (ii.intValue() != 0)
                mTab.setValue("M_PriceList_ID", Integer.valueOf(ii.intValue())); 
            } 
          } 
        } 
        mTab.setValue("Bill_BPartner_ID", C_BPartner_ID);
        int shipTo_ID = 0;
        int bill_Location_ID = 0;
        if (C_BPartner_ID.toString().equals(Env.getContext(ctx, WindowNo, 1113, "C_BPartner_ID"))) {
          String loc = Env.getContext(ctx, WindowNo, 1113, "C_BPartner_Location_ID");
          int locationId = 0;
          if (loc.length() > 0)
            locationId = Integer.parseInt(loc); 
          if (locationId > 0) {
            MBPartnerLocation bpLocation = new MBPartnerLocation(ctx, locationId, null);
            if (bpLocation.isBillTo())
              bill_Location_ID = locationId; 
            if (bpLocation.isShipTo())
              shipTo_ID = locationId; 
          } 
        } 
        if (bill_Location_ID == 0)
          bill_Location_ID = rs.getInt("Bill_Location_ID"); 
        if (bill_Location_ID == 0) {
          mTab.setValue("Bill_Location_ID", null);
        } else {
          mTab.setValue("Bill_Location_ID", Integer.valueOf(bill_Location_ID));
        } 
        if (shipTo_ID == 0)
          shipTo_ID = rs.getInt("C_BPartner_Location_ID"); 
        if (shipTo_ID == 0) {
          mTab.setValue("C_BPartner_Location_ID", null);
        } else {
          mTab.setValue("C_BPartner_Location_ID", Integer.valueOf(shipTo_ID));
        } 
        int contID = rs.getInt("AD_User_ID");
        if (C_BPartner_ID.toString().equals(Env.getContext(ctx, WindowNo, 1113, "C_BPartner_ID"))) {
          String cont = Env.getContext(ctx, WindowNo, 1113, "AD_User_ID");
          if (cont.length() > 0)
            contID = Integer.parseInt(cont); 
        } 
        if (contID == 0) {
          mTab.setValue("AD_User_ID", null);
        } else {
          mTab.setValue("AD_User_ID", Integer.valueOf(contID));
          mTab.setValue("Bill_User_ID", Integer.valueOf(contID));
        } 
        if (IsSOTrx) {
          double CreditLimit = rs.getDouble("SO_CreditLimit");
          if (CreditLimit != 0.0D) {
            double CreditAvailable = rs.getDouble("CreditAvailable");
            if (!rs.wasNull() && CreditAvailable < 0.0D)
              mTab.fireDataStatusEEvent("CreditLimitOver", 
                  DisplayType.getNumberFormat(12).format(CreditAvailable), 
                  false); 
          } 
        } 
        String s = rs.getString("POReference");
        if (s != null && s.length() != 0)
          mTab.setValue("POReference", s); 
        s = rs.getString("SO_Description");
        if (s != null && s.trim().length() != 0)
          mTab.setValue("Description", s); 
        s = rs.getString("IsDiscountPrinted");
        if (s != null && s.length() != 0) {
          mTab.setValue("IsDiscountPrinted", s);
        } else {
          mTab.setValue("IsDiscountPrinted", "N");
        } 
        String OrderType = Env.getContext(ctx, WindowNo, "OrderType");
        mTab.setValue("InvoiceRule", "D");
        mTab.setValue("DeliveryRule", "A");
        mTab.setValue("PaymentRule", "P");
        if (OrderType.equals("PR")) {
          mTab.setValue("InvoiceRule", "I");
          mTab.setValue("DeliveryRule", "R");
        } else if (OrderType.equals("WR")) {
          mTab.setValue("PaymentRule", "B");
        } else {
          s = rs.getString(IsSOTrx ? "PaymentRule" : "PaymentRulePO");
          if (s != null && s.length() != 0)
            mTab.setValue("PaymentRule", s); 
          ii = Integer.valueOf(rs.getInt(IsSOTrx ? "C_PaymentTerm_ID" : "PO_PaymentTerm_ID"));
          if (!rs.wasNull())
            mTab.setValue("C_PaymentTerm_ID", ii); 
          s = rs.getString("InvoiceRule");
          if (s != null && s.length() != 0)
            mTab.setValue("InvoiceRule", s); 
          s = rs.getString("DeliveryRule");
          if (s != null && s.length() != 0)
            mTab.setValue("DeliveryRule", s); 
          s = rs.getString("FreightCostRule");
          if (s != null && s.length() != 0)
            mTab.setValue("FreightCostRule", s); 
          s = rs.getString("DeliveryViaRule");
          if (s != null && s.length() != 0)
            mTab.setValue("DeliveryViaRule", s); 
        } 
      } 
    } catch (SQLException e) {
      this.log.log(Level.SEVERE, sql, e);
      return e.getLocalizedMessage();
    } finally {
      DB.close(rs, (Statement)cPreparedStatement);
      rs = null;
      cPreparedStatement = null;
    } 
    return "";
  }
  
  public String bPartnerBill(Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value) {
    CPreparedStatement cPreparedStatement = null;
    Integer bill_BPartner_ID = (Integer)value;
    if (bill_BPartner_ID == null || bill_BPartner_ID.intValue() == 0)
      return ""; 
    String sql = "SELECT p.AD_Language,p.C_PaymentTerm_ID,p.M_PriceList_ID,p.PaymentRule,p.POReference,p.SO_Description,p.IsDiscountPrinted,p.InvoiceRule,p.DeliveryRule,p.FreightCostRule,DeliveryViaRule,p.SO_CreditLimit, p.SO_CreditLimit-p.SO_CreditUsed AS CreditAvailable,(select max(c.AD_User_ID) from AD_User c where p.C_BPartner_ID=c.C_BPartner_ID AND c.IsActive='Y') as AD_User_ID,p.PO_PriceList_ID, p.PaymentRulePO, p.PO_PaymentTerm_ID,(select max(lbill.C_BPartner_Location_ID) from C_BPartner_Location lbill where p.C_BPartner_ID=lbill.C_BPartner_ID AND lbill.IsBillTo='Y' AND lbill.IsActive='Y') AS Bill_Location_ID FROM C_BPartner p WHERE p.C_BPartner_ID=? AND p.IsActive='Y'";
    boolean IsSOTrx = "Y".equals(Env.getContext(ctx, WindowNo, "IsSOTrx"));
    PreparedStatement pstmt = null;
    ResultSet rs = null;
    try {
      cPreparedStatement = DB.prepareStatement(sql, null);
      cPreparedStatement.setInt(1, bill_BPartner_ID.intValue());
      rs = cPreparedStatement.executeQuery();
      if (rs.next()) {
        Integer ii = Integer.valueOf(rs.getInt(IsSOTrx ? "M_PriceList_ID" : "PO_PriceList_ID"));
        if (!rs.wasNull()) {
          mTab.setValue("M_PriceList_ID", ii);
        } else {
          int i = Env.getContextAsInt(ctx, "#M_PriceList_ID");
          if (i != 0) {
            MPriceList pl = new MPriceList(ctx, i, null);
            if (IsSOTrx == pl.isSOPriceList()) {
              mTab.setValue("M_PriceList_ID", Integer.valueOf(i));
            } else {
              String sql2 = "SELECT M_PriceList_ID FROM M_PriceList WHERE AD_Client_ID=? AND IsSOPriceList=? AND IsActive='Y' ORDER BY IsDefault DESC";
              ii = Integer.valueOf(DB.getSQLValue(null, sql2, new Object[] { Integer.valueOf(Env.getAD_Client_ID(ctx)), Boolean.valueOf(IsSOTrx) }));
              if (ii.intValue() != 0)
                mTab.setValue("M_PriceList_ID", Integer.valueOf(ii.intValue())); 
            } 
          } 
        } 
        int bill_Location_ID = rs.getInt("Bill_Location_ID");
        if (bill_BPartner_ID.toString().equals(Env.getContext(ctx, WindowNo, 1113, "C_BPartner_ID"))) {
          int locationId = 0;
          String loc = Env.getContext(ctx, WindowNo, 1113, "C_BPartner_Location_ID");
          if (loc.length() > 0)
            locationId = Integer.parseInt(loc); 
          if (locationId > 0) {
            MBPartnerLocation bpLocation = new MBPartnerLocation(ctx, locationId, null);
            if (bpLocation.isBillTo())
              bill_Location_ID = locationId; 
          } 
        } 
        if (bill_Location_ID == 0) {
          mTab.setValue("Bill_Location_ID", null);
        } else {
          mTab.setValue("Bill_Location_ID", Integer.valueOf(bill_Location_ID));
        } 
        int contID = rs.getInt("AD_User_ID");
        if (bill_BPartner_ID.toString().equals(Env.getContext(ctx, WindowNo, 1113, "C_BPartner_ID"))) {
          String cont = Env.getContext(ctx, WindowNo, 1113, "AD_User_ID");
          if (cont.length() > 0)
            contID = Integer.parseInt(cont); 
        } 
        if (contID == 0) {
          mTab.setValue("Bill_User_ID", null);
        } else {
          mTab.setValue("Bill_User_ID", Integer.valueOf(contID));
        } 
        if (IsSOTrx) {
          double CreditLimit = rs.getDouble("SO_CreditLimit");
          if (CreditLimit != 0.0D) {
            double CreditAvailable = rs.getDouble("CreditAvailable");
            if (!rs.wasNull() && CreditAvailable < 0.0D)
              mTab.fireDataStatusEEvent("CreditLimitOver", 
                  DisplayType.getNumberFormat(12).format(CreditAvailable), 
                  false); 
          } 
        } 
        String s = rs.getString("POReference");
        if (s != null && s.length() != 0)
          mTab.setValue("POReference", s); 
        s = rs.getString("SO_Description");
        if (s != null && s.trim().length() != 0)
          mTab.setValue("Description", s); 
        s = rs.getString("IsDiscountPrinted");
        if (s != null && s.length() != 0) {
          mTab.setValue("IsDiscountPrinted", s);
        } else {
          mTab.setValue("IsDiscountPrinted", "N");
        } 
        String OrderType = Env.getContext(ctx, WindowNo, "OrderType");
        mTab.setValue("InvoiceRule", "D");
        mTab.setValue("PaymentRule", "P");
        if (OrderType.equals("PR")) {
          mTab.setValue("InvoiceRule", "I");
        } else if (OrderType.equals("WR")) {
          mTab.setValue("PaymentRule", "B");
        } else {
          s = rs.getString(IsSOTrx ? "PaymentRule" : "PaymentRulePO");
          if (s != null && s.length() != 0)
            mTab.setValue("PaymentRule", s); 
          ii = Integer.valueOf(rs.getInt(IsSOTrx ? "C_PaymentTerm_ID" : "PO_PaymentTerm_ID"));
          if (!rs.wasNull())
            mTab.setValue("C_PaymentTerm_ID", ii); 
          s = rs.getString("InvoiceRule");
          if (s != null && s.length() != 0)
            mTab.setValue("InvoiceRule", s); 
        } 
      } 
    } catch (SQLException e) {
      this.log.log(Level.SEVERE, "bPartnerBill", e);
      return e.getLocalizedMessage();
    } finally {
      DB.close(rs, (Statement)cPreparedStatement);
      rs = null;
      cPreparedStatement = null;
    } 
    return "";
  }
  
  public String warehouse(Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value) {
    if (isCalloutActive())
      return ""; 
    Integer M_Warehouse_ID = (Integer)value;
    if (M_Warehouse_ID == null || M_Warehouse_ID.intValue() == 0)
      return ""; 
    MWarehouse wh = MWarehouse.get(ctx, M_Warehouse_ID.intValue());
    String DeliveryRule = mTab.get_ValueAsString("DeliveryRule");
    if ((wh.isDisallowNegativeInv() && DeliveryRule.equals("F")) || 
      DeliveryRule == null || DeliveryRule.length() == 0)
      mTab.setValue("DeliveryRule", "A"); 
    return "";
  }
  
  public String priceListFill(Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value, boolean readonly) {
    Integer M_PriceList_ID = (Integer)mTab.getValue("M_PriceList_ID");
    if (M_PriceList_ID == null || M_PriceList_ID.intValue() == 0)
      return ""; 
    MPriceList pl = MPriceList.get(ctx, M_PriceList_ID.intValue(), null);
    if (pl != null && pl.getM_PriceList_ID() == M_PriceList_ID.intValue()) {
      if (!readonly) {
        mTab.setValue("IsTaxIncluded", Boolean.valueOf(pl.isTaxIncluded()));
        mTab.setValue("C_Currency_ID", Integer.valueOf(pl.getC_Currency_ID()));
      } 
      Env.setContext(ctx, WindowNo, "EnforcePriceLimit", pl.isEnforcePriceLimit());
      Timestamp date = null;
      if (mTab.getAD_Table_ID() == 259) {
        date = Env.getContextAsDate(ctx, WindowNo, "DateOrdered");
      } else if (mTab.getAD_Table_ID() == 318) {
        date = Env.getContextAsDate(ctx, WindowNo, "DateInvoiced");
      } 
      MPriceListVersion plv = pl.getPriceListVersion(date);
      if (plv != null && plv.getM_PriceList_Version_ID() > 0) {
        Env.setContext(ctx, WindowNo, "M_PriceList_Version_ID", plv.getM_PriceList_Version_ID());
      } else {
//        Env.setContext(Env.getCtx(), context, value);, context, value);
    	  
//    	  Env.setContext(Env.getCtx(), WindowNo, "M_PriceList_Version_ID", null);
      } 
    } 
    return "";
  }
  
  public String priceList(Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value) {
    return priceListFill(ctx, WindowNo, mTab, mField, value, false);
  }
  
  public String priceListReadOnly(Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value) {
    return priceListFill(ctx, WindowNo, mTab, mField, value, true);
  }
  
  @Deprecated
  public String paymentTerm(Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value) {
    Integer C_PaymentTerm_ID = (Integer)value;
    int C_Order_ID = Env.getContextAsInt(ctx, WindowNo, "C_Order_ID");
    if (C_PaymentTerm_ID == null || C_PaymentTerm_ID.intValue() == 0 || 
      C_Order_ID == 0)
      return ""; 
    MPaymentTerm pt = new MPaymentTerm(ctx, C_PaymentTerm_ID.intValue(), null);
    if (pt.get_ID() == 0)
      return "PaymentTerm not found"; 
    boolean valid = pt.applyOrder(C_Order_ID);
    mTab.setValue("IsPayScheduleValid", valid ? "Y" : "N");
    return "";
  }
  
  public String product(Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value) {
    Integer M_Product_ID = (Integer)value;
    if (M_Product_ID == null || M_Product_ID.intValue() == 0)
      return ""; 
    if (this.steps)
      this.log.warning("init"); 
    mTab.setValue("C_Charge_ID", null);
    if (Env.getContextAsInt(ctx, WindowNo, 1113, "M_Product_ID") == M_Product_ID.intValue() && 
      Env.getContextAsInt(ctx, WindowNo, 1113, "M_AttributeSetInstance_ID") != 0) {
      mTab.setValue("M_AttributeSetInstance_ID", Integer.valueOf(Env.getContextAsInt(ctx, WindowNo, 1113, "M_AttributeSetInstance_ID")));
    } else {
      mTab.setValue("M_AttributeSetInstance_ID", null);
    } 
    int C_BPartner_ID = Env.getContextAsInt(ctx, WindowNo, "C_BPartner_ID");
    BigDecimal Qty = (BigDecimal)mTab.getValue("QtyOrdered");
    boolean IsSOTrx = Env.getContext(ctx, WindowNo, "IsSOTrx").equals("Y");
    IProductPricing pp = Core.getProductPricing();
    pp.setInitialValues(M_Product_ID.intValue(), C_BPartner_ID, Qty, IsSOTrx, null);
    Timestamp orderDate = (Timestamp)mTab.getValue("DateOrdered");
    pp.setPriceDate(orderDate);
    I_C_OrderLine orderLine = (I_C_OrderLine)GridTabWrapper.create(mTab, I_C_OrderLine.class);
    pp.setOrderLine(orderLine, null);
    int M_PriceList_ID = Env.getContextAsInt(ctx, WindowNo, "M_PriceList_ID");
    pp.setM_PriceList_ID(M_PriceList_ID);
    int M_PriceList_Version_ID = Env.getContextAsInt(ctx, WindowNo, "M_PriceList_Version_ID");
    if (M_PriceList_Version_ID == 0 && M_PriceList_ID > 0) {
      String str = "SELECT plv.M_PriceList_Version_ID FROM M_PriceList_Version plv WHERE plv.M_PriceList_ID=?  AND plv.ValidFrom <= ? ORDER BY plv.ValidFrom DESC";
      M_PriceList_Version_ID = DB.getSQLValueEx(null, str, new Object[] { Integer.valueOf(M_PriceList_ID), orderDate });
      if (M_PriceList_Version_ID > 0)
        Env.setContext(ctx, WindowNo, "M_PriceList_Version_ID", M_PriceList_Version_ID); 
    } 
    pp.setM_PriceList_Version_ID(M_PriceList_Version_ID);
    mTab.setValue("PriceList", pp.getPriceList());
    mTab.setValue("PriceLimit", pp.getPriceLimit());
    mTab.setValue("PriceActual", pp.getPriceStd());
    mTab.setValue("PriceEntered", pp.getPriceStd());
    String sql = "select coalesce(MRP, 0) from M_ProductPrice where m_product_id=" + M_Product_ID + " and M_PriceList_Version_ID = " + M_PriceList_Version_ID;
    BigDecimal mrp = DB.getSQLValueBD(null, sql, new Object[0]);
    mTab.setValue("MRP", mrp);
    mTab.setValue("C_Currency_ID", Integer.valueOf(pp.getC_Currency_ID()));
    mTab.setValue("Discount", pp.getDiscount());
    mTab.setValue("C_UOM_ID", Integer.valueOf(pp.getC_UOM_ID()));
    mTab.setValue("QtyOrdered", mTab.getValue("QtyEntered"));
    Env.setContext(ctx, WindowNo, "EnforcePriceLimit", pp.isEnforcePriceLimit() ? "Y" : "N");
    Env.setContext(ctx, WindowNo, "DiscountSchema", pp.isDiscountSchema() ? "Y" : "N");
    if (Env.isSOTrx(ctx, WindowNo)) {
      MProduct product = MProduct.get(ctx, M_Product_ID.intValue());
      if (product.isStocked() && Env.getContext(ctx, WindowNo, "IsDropShip").equals("N")) {
        BigDecimal QtyOrdered = (BigDecimal)mTab.getValue("QtyOrdered");
        if (QtyOrdered == null)
          QtyOrdered = Env.ZERO; 
        int M_Warehouse_ID = Env.getContextAsInt(ctx, WindowNo, "M_Warehouse_ID");
        int M_AttributeSetInstance_ID = Env.getContextAsInt(ctx, WindowNo, mTab.getTabNo(), "M_AttributeSetInstance_ID");
        BigDecimal available = MStorageReservation.getQtyAvailable(
            M_Warehouse_ID, M_Product_ID.intValue(), M_AttributeSetInstance_ID, null);
        if (available == null)
          available = Env.ZERO; 
        if (available.signum() == 0) {
          mTab.fireDataStatusEEvent("NoQtyAvailable", "0", false);
        } else if (available.compareTo(QtyOrdered) < 0) {
          mTab.fireDataStatusEEvent("InsufficientQtyAvailable", available.toString(), false);
        } else {
          Integer C_OrderLine_ID = (Integer)mTab.getValue("C_OrderLine_ID");
          if (C_OrderLine_ID == null)
            C_OrderLine_ID = Integer.valueOf(0); 
          BigDecimal notReserved = MOrderLine.getNotReserved(ctx, 
              M_Warehouse_ID, M_Product_ID.intValue(), M_AttributeSetInstance_ID, 
              C_OrderLine_ID.intValue());
          if (notReserved == null)
            notReserved = Env.ZERO; 
          BigDecimal total = available.subtract(notReserved);
          if (total.compareTo(QtyOrdered) < 0) {
            String info = Msg.parseTranslation(ctx, "@QtyAvailable@=" + available + 
                " - @QtyNotReserved@=" + notReserved + " = " + total);
            mTab.fireDataStatusEEvent("InsufficientQtyAvailable", 
                info, false);
          } 
        } 
      } 
    } 
    if (this.steps)
      this.log.warning("fini"); 
    return tax(ctx, WindowNo, mTab, mField, value);
  }
  
  public String charge(Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value) {
    CPreparedStatement cPreparedStatement = null;
    Integer C_Charge_ID = (Integer)value;
    if (C_Charge_ID == null || C_Charge_ID.intValue() == 0)
      return ""; 
    if (mTab.getValue("M_Product_ID") != null) {
      mTab.setValue("C_Charge_ID", null);
      return "ChargeExclusively";
    } 
    mTab.setValue("M_AttributeSetInstance_ID", null);
    mTab.setValue("S_ResourceAssignment_ID", null);
    mTab.setValue("C_UOM_ID", Integer.valueOf(100));
    Env.setContext(ctx, WindowNo, "DiscountSchema", "N");
    String sql = "SELECT ChargeAmt FROM C_Charge WHERE C_Charge_ID=?";
    PreparedStatement pstmt = null;
    ResultSet rs = null;
    try {
      cPreparedStatement = DB.prepareStatement(sql, null);
      cPreparedStatement.setInt(1, C_Charge_ID.intValue());
      rs = cPreparedStatement.executeQuery();
      if (rs.next()) {
        mTab.setValue("PriceEntered", rs.getBigDecimal(1));
        mTab.setValue("PriceActual", rs.getBigDecimal(1));
        mTab.setValue("PriceLimit", Env.ZERO);
        mTab.setValue("PriceList", Env.ZERO);
        mTab.setValue("Discount", Env.ZERO);
      } 
    } catch (SQLException e) {
      this.log.log(Level.SEVERE, sql, e);
      return e.getLocalizedMessage();
    } finally {
      DB.close(rs, (Statement)cPreparedStatement);
      rs = null;
      cPreparedStatement = null;
    } 
    return tax(ctx, WindowNo, mTab, mField, value);
  }
  
  public String tax(Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value) {
    String column = mField.getColumnName();
    if (value == null)
      return ""; 
    if (this.steps)
      this.log.warning("init"); 
    int M_Product_ID = 0;
    if (column.equals("M_Product_ID")) {
      M_Product_ID = ((Integer)value).intValue();
    } else {
      M_Product_ID = Env.getContextAsInt(ctx, WindowNo, mTab.getTabNo(), "M_Product_ID");
    } 
    int C_Charge_ID = 0;
    if (column.equals("C_Charge_ID")) {
      C_Charge_ID = ((Integer)value).intValue();
    } else {
      C_Charge_ID = Env.getContextAsInt(ctx, WindowNo, mTab.getTabNo(), "C_Charge_ID");
    } 
    if (this.log.isLoggable(Level.FINE))
      this.log.fine("Product=" + M_Product_ID + ", C_Charge_ID=" + C_Charge_ID); 
    if (M_Product_ID == 0 && C_Charge_ID == 0)
      return amt(ctx, WindowNo, mTab, mField, value); 
    int shipC_BPartner_Location_ID = 0;
    if (column.equals("C_BPartner_Location_ID")) {
      shipC_BPartner_Location_ID = ((Integer)value).intValue();
    } else {
      shipC_BPartner_Location_ID = Env.getContextAsInt(ctx, WindowNo, "C_BPartner_Location_ID");
    } 
    if (shipC_BPartner_Location_ID == 0)
      return amt(ctx, WindowNo, mTab, mField, value); 
    if (this.log.isLoggable(Level.FINE))
      this.log.fine("Ship BP_Location=" + shipC_BPartner_Location_ID); 
    Timestamp billDate = Env.getContextAsDate(ctx, WindowNo, "DateOrdered");
    if (this.log.isLoggable(Level.FINE))
      this.log.fine("Bill Date=" + billDate); 
    Timestamp shipDate = Env.getContextAsDate(ctx, WindowNo, "DatePromised");
    if (this.log.isLoggable(Level.FINE))
      this.log.fine("Ship Date=" + shipDate); 
    int AD_Org_ID = Env.getContextAsInt(ctx, WindowNo, "AD_Org_ID");
    if (this.log.isLoggable(Level.FINE))
      this.log.fine("Org=" + AD_Org_ID); 
    int M_Warehouse_ID = Env.getContextAsInt(ctx, WindowNo, "M_Warehouse_ID");
    if (this.log.isLoggable(Level.FINE))
      this.log.fine("Warehouse=" + M_Warehouse_ID); 
    int billC_BPartner_Location_ID = Env.getContextAsInt(ctx, WindowNo, "Bill_Location_ID");
    if (billC_BPartner_Location_ID == 0)
      billC_BPartner_Location_ID = shipC_BPartner_Location_ID; 
    if (this.log.isLoggable(Level.FINE))
      this.log.fine("Bill BP_Location=" + billC_BPartner_Location_ID); 
    int C_Tax_ID = Tax.get(ctx, M_Product_ID, C_Charge_ID, billDate, shipDate, 
        AD_Org_ID, M_Warehouse_ID, billC_BPartner_Location_ID, shipC_BPartner_Location_ID, 
        "Y".equals(Env.getContext(ctx, WindowNo, "IsSOTrx")), null);
    if (this.log.isLoggable(Level.INFO))
      this.log.info("Tax ID=" + C_Tax_ID); 
    if (C_Tax_ID == 0) {
      mTab.fireDataStatusEEvent(CLogger.retrieveError());
    } else {
      mTab.setValue("C_Tax_ID", Integer.valueOf(C_Tax_ID));
    } 
    if (this.steps)
      this.log.warning("fini"); 
    return amt(ctx, WindowNo, mTab, mField, value);
  }
  
  public String amt(Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value) {
    if (isCalloutActive() || value == null)
      return ""; 
    if (this.steps)
      this.log.warning("init"); 
    int C_UOM_To_ID = Env.getContextAsInt(ctx, WindowNo, mTab.getTabNo(), "C_UOM_ID");
    int M_Product_ID = Env.getContextAsInt(ctx, WindowNo, mTab.getTabNo(), "M_Product_ID");
    int M_PriceList_ID = Env.getContextAsInt(ctx, WindowNo, mTab.getTabNo(), "M_PriceList_ID");
    int StdPrecision = MPriceList.getStandardPrecision(ctx, M_PriceList_ID);
    MPriceList pl = new MPriceList(ctx, M_PriceList_ID, null);
    boolean isEnforcePriceLimit = pl.isEnforcePriceLimit();
    BigDecimal QtyEntered = (BigDecimal)mTab.getValue("QtyEntered");
    if (QtyEntered == null)
      QtyEntered = Env.ZERO; 
    BigDecimal QtyOrdered = (BigDecimal)mTab.getValue("QtyOrdered");
    if (QtyOrdered == null)
      QtyOrdered = Env.ZERO; 
    if (this.log.isLoggable(Level.FINE))
      this.log.fine("QtyEntered=" + QtyEntered + ", Ordered=" + QtyOrdered + ", UOM=" + C_UOM_To_ID); 
    BigDecimal PriceEntered = (BigDecimal)mTab.getValue("PriceEntered");
    BigDecimal PriceActual = (BigDecimal)mTab.getValue("PriceActual");
    BigDecimal Discount = (BigDecimal)mTab.getValue("Discount");
    BigDecimal PriceLimit = (BigDecimal)mTab.getValue("PriceLimit");
    BigDecimal PriceList = (BigDecimal)mTab.getValue("PriceList");
    if (this.log.isLoggable(Level.FINE)) {
      this.log.fine("PriceList=" + PriceList + ", Limit=" + PriceLimit + ", Precision=" + StdPrecision);
      this.log.fine("PriceEntered=" + PriceEntered + ", Actual=" + PriceActual + ", Discount=" + Discount);
    } 
    if (M_Product_ID == 0) {
      if (mField.getColumnName().equals("PriceActual")) {
        PriceEntered = (BigDecimal)value;
        mTab.setValue("PriceEntered", value);
      } else if (mField.getColumnName().equals("PriceEntered")) {
        PriceActual = (BigDecimal)value;
        mTab.setValue("PriceActual", value);
      } 
    } else if ((mField.getColumnName().equals("QtyOrdered") || 
      mField.getColumnName().equals("QtyEntered") || 
      mField.getColumnName().equals("C_UOM_ID") || 
      mField.getColumnName().equals("M_Product_ID")) && 
      !"N".equals(Env.getContext(ctx, WindowNo, "DiscountSchema"))) {
      int C_BPartner_ID = Env.getContextAsInt(ctx, WindowNo, "C_BPartner_ID");
      if (mField.getColumnName().equals("QtyEntered"))
        QtyOrdered = MUOMConversion.convertProductFrom(ctx, M_Product_ID, 
            C_UOM_To_ID, QtyEntered); 
      if (QtyOrdered == null)
        QtyOrdered = QtyEntered; 
      boolean IsSOTrx = Env.getContext(ctx, WindowNo, "IsSOTrx").equals("Y");
      IProductPricing pp = Core.getProductPricing();
      pp.setInitialValues(M_Product_ID, C_BPartner_ID, QtyOrdered, IsSOTrx, null);
      Timestamp date = (Timestamp)mTab.getValue("DateOrdered");
      pp.setPriceDate(date);
      I_C_OrderLine orderLine = (I_C_OrderLine)GridTabWrapper.create(mTab, I_C_OrderLine.class);
      pp.setOrderLine(orderLine, null);
      pp.setM_PriceList_ID(M_PriceList_ID);
      int M_PriceList_Version_ID = Env.getContextAsInt(ctx, WindowNo, "M_PriceList_Version_ID");
      pp.setM_PriceList_Version_ID(M_PriceList_Version_ID);
      PriceEntered = MUOMConversion.convertProductFrom(ctx, M_Product_ID, 
          C_UOM_To_ID, pp.getPriceStd());
      if (PriceEntered == null)
        PriceEntered = pp.getPriceStd(); 
      if (this.log.isLoggable(Level.FINE))
        this.log.fine("QtyChanged -> PriceActual=" + pp.getPriceStd() + 
            ", PriceEntered=" + PriceEntered + ", Discount=" + pp.getDiscount()); 
      PriceActual = pp.getPriceStd();
      Discount = pp.getDiscount();
      PriceLimit = pp.getPriceLimit();
      PriceList = pp.getPriceList();
      mTab.setValue("PriceList", pp.getPriceList());
      mTab.setValue("PriceLimit", pp.getPriceLimit());
      mTab.setValue("PriceActual", pp.getPriceStd());
      mTab.setValue("Discount", pp.getDiscount());
      mTab.setValue("PriceEntered", PriceEntered);
      Env.setContext(ctx, WindowNo, "DiscountSchema", pp.isDiscountSchema() ? "Y" : "N");
    } else if (mField.getColumnName().equals("PriceActual")) {
      PriceActual = (BigDecimal)value;
      PriceEntered = MUOMConversion.convertProductFrom(ctx, M_Product_ID, 
          C_UOM_To_ID, PriceActual);
      if (PriceEntered == null)
        PriceEntered = PriceActual; 
      if (this.log.isLoggable(Level.FINE))
        this.log.fine("PriceActual=" + PriceActual + 
            " -> PriceEntered=" + PriceEntered); 
      mTab.setValue("PriceEntered", PriceEntered);
    } else if (mField.getColumnName().equals("PriceEntered")) {
      PriceEntered = (BigDecimal)value;
      PriceActual = MUOMConversion.convertProductTo(ctx, M_Product_ID, 
          C_UOM_To_ID, PriceEntered);
      if (PriceActual == null)
        PriceActual = PriceEntered; 
      if (this.log.isLoggable(Level.FINE))
        this.log.fine("PriceEntered=" + PriceEntered + 
            " -> PriceActual=" + PriceActual); 
      mTab.setValue("PriceActual", PriceActual);
    } 
    if (mField.getColumnName().equals("Discount")) {
      if (PriceList.doubleValue() != 0.0D)
        PriceActual = BigDecimal.valueOf((100.0D - Discount.doubleValue()) / 100.0D * PriceList.doubleValue()); 
      if (PriceActual.scale() > StdPrecision)
        PriceActual = PriceActual.setScale(StdPrecision, RoundingMode.HALF_UP); 
      PriceEntered = MUOMConversion.convertProductFrom(ctx, M_Product_ID, 
          C_UOM_To_ID, PriceActual);
      if (PriceEntered == null)
        PriceEntered = PriceActual; 
      mTab.setValue("PriceActual", PriceActual);
      mTab.setValue("PriceEntered", PriceEntered);
    } else {
      if (PriceList.compareTo(Env.ZERO) == 0) {
        Discount = Env.ZERO;
      } else {
        Discount = BigDecimal.valueOf((PriceList.doubleValue() - PriceActual.doubleValue()) / PriceList.doubleValue() * 100.0D);
      } 
      if (Discount.scale() > 2)
        Discount = Discount.setScale(2, RoundingMode.HALF_UP); 
      mTab.setValue("Discount", Discount);
    } 
    if (this.log.isLoggable(Level.FINE))
      this.log.fine("PriceEntered=" + PriceEntered + ", Actual=" + PriceActual + ", Discount=" + Discount); 
    String epl = Env.getContext(ctx, WindowNo, "EnforcePriceLimit");
    boolean enforce = (Env.isSOTrx(ctx, WindowNo) && epl != null && !epl.equals("")) ? epl.equals("Y") : isEnforcePriceLimit;
    if (enforce && MRole.getDefault().isOverwritePriceLimit())
      enforce = false; 
    if (enforce && PriceLimit.doubleValue() != 0.0D && 
      PriceActual.compareTo(PriceLimit) < 0) {
      PriceActual = PriceLimit;
      PriceEntered = MUOMConversion.convertProductFrom(ctx, M_Product_ID, 
          C_UOM_To_ID, PriceLimit);
      if (PriceEntered == null)
        PriceEntered = PriceLimit; 
      if (this.log.isLoggable(Level.FINE))
        this.log.fine("(under) PriceEntered=" + PriceEntered + ", Actual" + PriceLimit); 
      mTab.setValue("PriceActual", PriceLimit);
      mTab.setValue("PriceEntered", PriceEntered);
      mTab.fireDataStatusEEvent("UnderLimitPrice", "", false);
      if (PriceList.compareTo(Env.ZERO) != 0) {
        Discount = BigDecimal.valueOf((PriceList.doubleValue() - PriceActual.doubleValue()) / PriceList.doubleValue() * 100.0D);
        if (Discount.scale() > 2)
          Discount = Discount.setScale(2, RoundingMode.HALF_UP); 
        mTab.setValue("Discount", Discount);
      } 
    } 
    BigDecimal LineNetAmt = QtyOrdered.multiply(PriceActual);
    if (LineNetAmt.scale() > StdPrecision)
      LineNetAmt = LineNetAmt.setScale(StdPrecision, RoundingMode.HALF_UP); 
    if (this.log.isLoggable(Level.INFO))
      this.log.info("LineNetAmt=" + LineNetAmt); 
    mTab.setValue("LineNetAmt", LineNetAmt);
    return "";
  }
  
  public String qty(Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value) {
    if (isCalloutActive() || value == null)
      return ""; 
    int M_Product_ID = Env.getContextAsInt(ctx, WindowNo, mTab.getTabNo(), "M_Product_ID");
    if (this.steps)
      this.log.warning("init - M_Product_ID=" + M_Product_ID + " - "); 
    BigDecimal QtyOrdered = Env.ZERO;
    if (M_Product_ID == 0) {
      BigDecimal QtyEntered = (BigDecimal)mTab.getValue("QtyEntered");
      QtyOrdered = QtyEntered;
      mTab.setValue("QtyOrdered", QtyOrdered);
    } else if (mField.getColumnName().equals("C_UOM_ID")) {
      int C_UOM_To_ID = ((Integer)value).intValue();
      BigDecimal QtyEntered = (BigDecimal)mTab.getValue("QtyEntered");
      BigDecimal QtyEntered1 = QtyEntered.setScale(MUOM.getPrecision(ctx, C_UOM_To_ID), RoundingMode.HALF_UP);
      if (QtyEntered.compareTo(QtyEntered1) != 0) {
        if (this.log.isLoggable(Level.FINE))
          this.log.fine("Corrected QtyEntered Scale UOM=" + C_UOM_To_ID + 
              "; QtyEntered=" + QtyEntered + "->" + QtyEntered1); 
        QtyEntered = QtyEntered1;
        mTab.setValue("QtyEntered", QtyEntered);
      } 
      QtyOrdered = MUOMConversion.convertProductFrom(ctx, M_Product_ID, 
          C_UOM_To_ID, QtyEntered);
      if (QtyOrdered == null)
        QtyOrdered = QtyEntered; 
      boolean conversion = (QtyEntered.compareTo(QtyOrdered) != 0);
      BigDecimal PriceActual = (BigDecimal)mTab.getValue("PriceActual");
      BigDecimal PriceEntered = MUOMConversion.convertProductFrom(ctx, M_Product_ID, 
          C_UOM_To_ID, PriceActual);
      if (PriceEntered == null)
        PriceEntered = PriceActual; 
      if (this.log.isLoggable(Level.FINE))
        this.log.fine("UOM=" + C_UOM_To_ID + 
            ", QtyEntered/PriceActual=" + QtyEntered + "/" + PriceActual + 
            " -> " + conversion + 
            " QtyOrdered/PriceEntered=" + QtyOrdered + "/" + PriceEntered); 
      Env.setContext(ctx, WindowNo, "UOMConversion", conversion ? "Y" : "N");
      mTab.setValue("QtyOrdered", QtyOrdered);
      mTab.setValue("PriceEntered", PriceEntered);
    } else if (mField.getColumnName().equals("QtyEntered")) {
      int C_UOM_To_ID = Env.getContextAsInt(ctx, WindowNo, mTab.getTabNo(), "C_UOM_ID");
      BigDecimal QtyEntered = (BigDecimal)value;
      BigDecimal QtyEntered1 = QtyEntered.setScale(MUOM.getPrecision(ctx, C_UOM_To_ID), RoundingMode.HALF_UP);
      if (QtyEntered.compareTo(QtyEntered1) != 0) {
        if (this.log.isLoggable(Level.FINE))
          this.log.fine("Corrected QtyEntered Scale UOM=" + C_UOM_To_ID + 
              "; QtyEntered=" + QtyEntered + "->" + QtyEntered1); 
        QtyEntered = QtyEntered1;
        mTab.setValue("QtyEntered", QtyEntered);
      } 
      QtyOrdered = MUOMConversion.convertProductFrom(ctx, M_Product_ID, 
          C_UOM_To_ID, QtyEntered);
      if (QtyOrdered == null)
        QtyOrdered = QtyEntered; 
      boolean conversion = (QtyEntered.compareTo(QtyOrdered) != 0);
      if (this.log.isLoggable(Level.FINE))
        this.log.fine("UOM=" + C_UOM_To_ID + 
            ", QtyEntered=" + QtyEntered + 
            " -> " + conversion + 
            " QtyOrdered=" + QtyOrdered); 
      Env.setContext(ctx, WindowNo, "UOMConversion", conversion ? "Y" : "N");
      mTab.setValue("QtyOrdered", QtyOrdered);
    } else if (mField.getColumnName().equals("QtyOrdered")) {
      int C_UOM_To_ID = Env.getContextAsInt(ctx, WindowNo, mTab.getTabNo(), "C_UOM_ID");
      QtyOrdered = (BigDecimal)value;
      int precision = MProduct.get(ctx, M_Product_ID).getUOMPrecision();
      BigDecimal QtyOrdered1 = QtyOrdered.setScale(precision, RoundingMode.HALF_UP);
      if (QtyOrdered.compareTo(QtyOrdered1) != 0) {
        if (this.log.isLoggable(Level.FINE))
          this.log.fine("Corrected QtyOrdered Scale " + 
              QtyOrdered + "->" + QtyOrdered1); 
        QtyOrdered = QtyOrdered1;
        mTab.setValue("QtyOrdered", QtyOrdered);
      } 
      BigDecimal QtyEntered = MUOMConversion.convertProductTo(ctx, M_Product_ID, 
          C_UOM_To_ID, QtyOrdered);
      if (QtyEntered == null)
        QtyEntered = QtyOrdered; 
      boolean conversion = (QtyOrdered.compareTo(QtyEntered) != 0);
      if (this.log.isLoggable(Level.FINE))
        this.log.fine("UOM=" + C_UOM_To_ID + 
            ", QtyOrdered=" + QtyOrdered + 
            " -> " + conversion + 
            " QtyEntered=" + QtyEntered); 
      Env.setContext(ctx, WindowNo, "UOMConversion", conversion ? "Y" : "N");
      mTab.setValue("QtyEntered", QtyEntered);
    } else {
      QtyOrdered = (BigDecimal)mTab.getValue("QtyOrdered");
    } 
    if (M_Product_ID != 0 && 
      Env.isSOTrx(ctx, WindowNo) && 
      QtyOrdered.signum() > 0) {
      MProduct product = MProduct.get(ctx, M_Product_ID);
      if (product.isStocked() && Env.getContext(ctx, WindowNo, "IsDropShip").equals("N")) {
        int M_Warehouse_ID = Env.getContextAsInt(ctx, WindowNo, "M_Warehouse_ID");
        int M_AttributeSetInstance_ID = Env.getContextAsInt(ctx, WindowNo, mTab.getTabNo(), "M_AttributeSetInstance_ID");
        BigDecimal available = MStorageReservation.getQtyAvailable(
            M_Warehouse_ID, M_Product_ID, M_AttributeSetInstance_ID, null);
        if (available == null)
          available = Env.ZERO; 
        if (available.signum() == 0) {
          mTab.fireDataStatusEEvent("NoQtyAvailable", "0", false);
        } else if (available.compareTo(QtyOrdered) < 0) {
          mTab.fireDataStatusEEvent("InsufficientQtyAvailable", available.toString(), false);
        } else {
          Integer C_OrderLine_ID = (Integer)mTab.getValue("C_OrderLine_ID");
          if (C_OrderLine_ID == null)
            C_OrderLine_ID = Integer.valueOf(0); 
          BigDecimal notReserved = MOrderLine.getNotReserved(ctx, 
              M_Warehouse_ID, M_Product_ID, M_AttributeSetInstance_ID, 
              C_OrderLine_ID.intValue());
          if (notReserved == null)
            notReserved = Env.ZERO; 
          BigDecimal total = available.subtract(notReserved);
          if (total.compareTo(QtyOrdered) < 0) {
            StringBuilder msgpts = (new StringBuilder("@QtyAvailable@=")).append(available)
              .append("  -  @QtyNotReserved@=").append(notReserved).append("  =  ").append(total);
            String info = Msg.parseTranslation(ctx, msgpts.toString());
            mTab.fireDataStatusEEvent("InsufficientQtyAvailable", 
                info, false);
          } 
        } 
      } 
    } 
    return "";
  }
  
  public String SalesOrderTenderType(Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value, Object oldValue) {
    this.log.info("");
    if (value == null)
      return ""; 
    int tendertype_id = ((Integer)value).intValue();
    X_C_POSTenderType tendertype = new X_C_POSTenderType(ctx, tendertype_id, null);
    mTab.setValue("IsPostDated", Boolean.valueOf(tendertype.isPostDated()));
    mTab.setValue("TenderType", tendertype.getTenderType());
    return "";
  }
  
  public String organization(Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value) {
    if (value == null || ((Integer)value).intValue() == 0)
      return ""; 
    this.log.info("Set default Warehouse for Organization " + value + " on Window " + WindowNo);
    Integer m_warehouse_id = (Integer)mTab.getValue("M_Warehouse_ID");
    if (m_warehouse_id == null || m_warehouse_id.intValue() == 0) {
      Integer ad_org_id = (Integer)value;
      MOrgInfo orginfo = MOrgInfo.get(ctx, ad_org_id.intValue(), null);
      if (orginfo != null && orginfo.getM_Warehouse_ID() != 0)
        mTab.setValue("M_Warehouse_ID", Integer.valueOf(orginfo.getM_Warehouse_ID())); 
    } 
    return "";
  }
  
  public String navigateOrderLine(Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value) {
    Integer M_Product_ID = Integer.valueOf(Env.getContextAsInt(ctx, WindowNo, mTab.getTabNo(), "M_Product_ID"));
    if (M_Product_ID == null || M_Product_ID.intValue() == 0) {
      Env.setContext(ctx, WindowNo, "DiscountSchema", "N");
      return "";
    } 
    int C_BPartner_ID = Env.getContextAsInt(ctx, WindowNo, "C_BPartner_ID");
    BigDecimal Qty = (BigDecimal)mTab.getValue("QtyOrdered");
    boolean IsSOTrx = Env.getContext(ctx, WindowNo, "IsSOTrx").equals("Y");
    IProductPricing pp = Core.getProductPricing();
    pp.setInitialValues(M_Product_ID.intValue(), C_BPartner_ID, Qty, IsSOTrx, null);
    Timestamp orderDate = (Timestamp)mTab.getValue("DateOrdered");
    pp.setPriceDate(orderDate);
    I_C_OrderLine orderLine = (I_C_OrderLine)GridTabWrapper.create(mTab, I_C_OrderLine.class);
    pp.setOrderLine(orderLine, null);
    int M_PriceList_ID = Env.getContextAsInt(ctx, WindowNo, "M_PriceList_ID");
    pp.setM_PriceList_ID(M_PriceList_ID);
    int M_PriceList_Version_ID = Env.getContextAsInt(ctx, WindowNo, "M_PriceList_Version_ID");
    if (M_PriceList_Version_ID == 0 && M_PriceList_ID > 0) {
      String sql = "SELECT plv.M_PriceList_Version_ID FROM M_PriceList_Version plv WHERE plv.M_PriceList_ID=?  AND plv.ValidFrom <= ? ORDER BY plv.ValidFrom DESC";
      M_PriceList_Version_ID = DB.getSQLValueEx(null, sql, new Object[] { Integer.valueOf(M_PriceList_ID), orderDate });
      if (M_PriceList_Version_ID > 0)
        Env.setContext(ctx, WindowNo, "M_PriceList_Version_ID", M_PriceList_Version_ID); 
    } 
    pp.setM_PriceList_Version_ID(M_PriceList_Version_ID);
    Env.setContext(ctx, WindowNo, "EnforcePriceLimit", pp.isEnforcePriceLimit() ? "Y" : "N");
    Env.setContext(ctx, WindowNo, "DiscountSchema", pp.isDiscountSchema() ? "Y" : "N");
    return "";
  }
}
