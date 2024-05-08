package org.tax.providers;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.logging.Level;
import org.adempiere.model.ITaxProvider;
import org.compiere.model.MInvoice;
import org.compiere.model.MInvoiceLine;
import org.compiere.model.MInvoiceTax;
import org.compiere.model.MOrder;
import org.compiere.model.MOrderLine;
import org.compiere.model.MOrderTax;
import org.compiere.model.MRMA;
import org.compiere.model.MRMALine;
import org.compiere.model.MTax;
import org.compiere.model.MTaxProvider;
import org.compiere.process.ProcessInfo;
import org.compiere.util.CLogger;
import org.compiere.util.CPreparedStatement;
import org.compiere.util.DB;
import org.compiere.util.Env;

public class ERInsightTaxProvider implements ITaxProvider {
  protected transient CLogger log = CLogger.getCLogger(getClass());
  
  public boolean calculateOrderTaxTotal(MTaxProvider provider, MOrder order) {
    BigDecimal totalLines = Env.ZERO;
    ArrayList<Integer> taxList = new ArrayList<>();
    MOrderLine[] lines = order.getLines();
    for (int i = 0; i < lines.length; i++) {
      MOrderLine line = lines[i];
      totalLines = totalLines.add(line.getLineNetAmt());
      Integer taxID = Integer.valueOf(line.getC_Tax_ID());
      if (!taxList.contains(taxID)) {
        MTax tax = new MTax(order.getCtx(), taxID.intValue(), order.get_TrxName());
        MOrderTax oTax = MOrderTax.get(line, order.getPrecision(), false, order.get_TrxName());
        oTax.setIsTaxIncluded(order.isTaxIncluded());
        if (!calculateTaxFromLines(tax.isDocumentLevel(), tax, line, oTax))
          return false; 
        if (!oTax.save(order.get_TrxName()))
          return false; 
        taxList.add(taxID);
      } 
    } 
    BigDecimal grandTotal = totalLines;
    MOrderTax[] taxes = order.getTaxes(true);
    for (int j = 0; j < taxes.length; j++) {
      MOrderTax oTax = taxes[j];
      if (oTax.getC_TaxProvider_ID() != 0) {
        if (!order.isTaxIncluded())
          grandTotal = grandTotal.add(oTax.getTaxAmt()); 
      } else {
        MTax tax = MTax.get(Env.getCtx(), oTax.getC_Tax_ID());
        if (tax.isSummary()) {
          MTax[] cTaxes = tax.getChildTaxes(false);
          for (int k = 0; k < cTaxes.length; k++) {
            MTax cTax = cTaxes[k];
//            BigDecimal parent_multiplier = tax.getRate().divide(Env.ONEHUNDRED);
//            BigDecimal base_amt = oTax.getTaxBaseAmt().divide(parent_multiplier.add(Env.ONE), MathContext.DECIMAL32);

            BigDecimal taxAmt = calculateTax(cTax, oTax.getTaxBaseAmt(), false, order.getPrecision());
//            BigDecimal taxAmt = cTax.calculateTax(oTax.getTaxBaseAmt(), false, order.getPrecision());
            MOrderTax newOTax = new MOrderTax(order.getCtx(), 0, order.get_TrxName());
            newOTax.setAD_Org_ID(order.getAD_Org_ID());
            newOTax.setC_Order_ID(order.getC_Order_ID());
            newOTax.setC_Tax_ID(cTax.getC_Tax_ID());
            newOTax.setIsTaxIncluded(order.isTaxIncluded());
            newOTax.setTaxBaseAmt(oTax.getTaxBaseAmt());
            newOTax.setTaxAmt(taxAmt);
            if (!newOTax.save(order.get_TrxName()))
              return false; 
            if (!order.isTaxIncluded())
              grandTotal = grandTotal.add(taxAmt); 
          } 
          if (!oTax.delete(true, order.get_TrxName()))
            return false; 
//          if (!oTax.save(order.get_TrxName()))
//            return false; 
        } else if (!order.isTaxIncluded()) {
          grandTotal = grandTotal.add(oTax.getTaxAmt());
        } 
      } 
    } 
    order.setTotalLines(totalLines);
    order.setGrandTotal(grandTotal);
    return true;
  }
  
  public boolean calculateTaxFromLines(boolean isDocLevel, MTax tax, MInvoiceLine oline, MInvoiceTax otax) {
    CPreparedStatement cPreparedStatement = null;
    BigDecimal taxBaseAmt = Env.ZERO;
    BigDecimal taxAmt = Env.ZERO;
    boolean documentLevel = isDocLevel;
    String sql = "SELECT coalesce((MRP * QtyEntered), 0) FROM C_InvoiceLine WHERE C_Invoice_ID=? AND C_Tax_ID=?";
    PreparedStatement pstmt = null;
    ResultSet rs = null;
    try {
      cPreparedStatement = DB.prepareStatement(sql, oline.get_TrxName());
      cPreparedStatement.setInt(1, oline.getC_Invoice_ID());
      cPreparedStatement.setInt(2, oline.getC_Tax_ID());
      rs = cPreparedStatement.executeQuery();
      while (rs.next()) {
        BigDecimal baseAmt = rs.getBigDecimal(1);
        taxBaseAmt = taxBaseAmt.add(baseAmt);
        if (!documentLevel)
          taxAmt = taxAmt.add(tax.calculateTax(baseAmt, oline.isTaxIncluded(), oline.getPrecision())); 
      } 
    } catch (Exception e) {
      this.log.log(Level.SEVERE, oline.get_TrxName(), e);
      taxBaseAmt = null;
    } finally {
      DB.close(rs, (Statement)cPreparedStatement);
      rs = null;
      cPreparedStatement = null;
    } 
    if (taxBaseAmt == null)
      return false; 
    if (documentLevel)
      taxAmt = calculateTax(tax, taxBaseAmt, oline.isTaxIncluded(), oline.getPrecision()); 
    otax.setTaxAmt(taxAmt);
    if (oline.isTaxIncluded()) {
      otax.setTaxBaseAmt(taxBaseAmt.subtract(taxAmt));
    } else {
      otax.setTaxBaseAmt(taxBaseAmt);
    } 
    if (this.log.isLoggable(Level.FINE))
      this.log.fine(toString()); 
    return true;
  }
  
  public boolean calculateTaxFromLines(boolean isDocLevel, MTax tax, MOrderLine oline, MOrderTax otax) {
    CPreparedStatement cPreparedStatement = null;
    BigDecimal taxBaseAmt = Env.ZERO;
    BigDecimal taxAmt = Env.ZERO;
    boolean documentLevel = isDocLevel;
    String sql = "SELECT coalesce((MRP * QtyEntered), 0) FROM C_OrderLine WHERE C_Order_ID=? AND C_Tax_ID=?";
    PreparedStatement pstmt = null;
    ResultSet rs = null;
    try {
      cPreparedStatement = DB.prepareStatement(sql, oline.get_TrxName());
      cPreparedStatement.setInt(1, oline.getC_Order_ID());
      cPreparedStatement.setInt(2, oline.getC_Tax_ID());
      rs = cPreparedStatement.executeQuery();
      while (rs.next()) {
        BigDecimal baseAmt = rs.getBigDecimal(1);
        taxBaseAmt = taxBaseAmt.add(baseAmt);
        if (!documentLevel)
          taxAmt = taxAmt.add(tax.calculateTax(baseAmt, oline.isTaxIncluded(), oline.getPrecision())); 
      } 
    } catch (Exception e) {
      this.log.log(Level.SEVERE, oline.get_TrxName(), e);
      taxBaseAmt = null;
    } finally {
      DB.close(rs, (Statement)cPreparedStatement);
      rs = null;
      cPreparedStatement = null;
    } 
    if (taxBaseAmt == null)
      return false; 
    if (documentLevel)
      taxAmt = calculateTax(tax, taxBaseAmt, oline.isTaxIncluded(), oline.getPrecision()); 
    otax.setTaxAmt(taxAmt);
    if (oline.isTaxIncluded()) {
      otax.setTaxBaseAmt(taxBaseAmt.subtract(taxAmt));
    } else {
      otax.setTaxBaseAmt(taxBaseAmt);
    } 
    if (this.log.isLoggable(Level.FINE))
      this.log.fine(toString()); 
    return true;
  }
  
  public BigDecimal calculateTax(MTax mtax, BigDecimal amount, boolean taxIncluded, int scale) {
    MTax[] taxarray;
    if (mtax.getRate().signum() == 0)
      return Env.ZERO; 
    if (mtax.isSummary()) {
      taxarray = mtax.getChildTaxes(false);
    } else {
      taxarray = new MTax[] { mtax };
    } 
    BigDecimal tax = Env.ZERO;
    byte b;
    int i;
    MTax[] arrayOfMTax1;
    for (i = (arrayOfMTax1 = taxarray).length, b = 0; b < i; ) {
      MTax taxc = arrayOfMTax1[b];
      BigDecimal multiplier = taxc.getRate().divide(Env.ONEHUNDRED);
      BigDecimal parent_multiplier = mtax.getRate().divide(Env.ONEHUNDRED);
      if(taxarray.length==1 && taxc.getParent_Tax()!=null && taxc.getParent_Tax().getRate().compareTo(Env.ZERO)!=0) {
    	  parent_multiplier = taxc.getParent_Tax().getRate().divide(Env.ONEHUNDRED);
      }
      
      if (!taxIncluded) {
    	  BigDecimal base_amt = amount.divide(parent_multiplier.add(Env.ONE), MathContext.DECIMAL32);
    	  BigDecimal newtax = base_amt.multiply(multiplier);
//        amount.multiply(multiplier).setScale(scale);
//        BigDecimal newtax = amount.subtract(amount.divide(multiplier.add(Env.ONE), MathContext.DECIMAL32));
        tax = tax.add(newtax);
      } else {
        multiplier = multiplier.add(Env.ONE);
        BigDecimal base = amount.divide(multiplier, 12, RoundingMode.HALF_UP);
        BigDecimal itax = amount.subtract(base).setScale(scale, RoundingMode.HALF_UP);
        tax = tax.add(itax);
      } 
      b++;
    } 
    if (this.log.isLoggable(Level.FINE))
      this.log.fine("calculateTax " + amount + 
          " (incl=" + taxIncluded + ",scale=" + scale + 
          ") = " + tax + " [" + tax + "]"); 
    return tax;
  }
  
  public boolean updateOrderTax(MTaxProvider provider, MOrderLine line) {
    MTax mtax = new MTax(line.getCtx(), line.getC_Tax_ID(), line.get_TrxName());
    MOrderTax tax = MOrderTax.get(line, line.getPrecision(), false, line.get_TrxName());
    if (tax != null) {
      if (!calculateTaxFromLines(mtax.isDocumentLevel(), mtax, line, tax))
        return false; 
      if (tax.getTaxAmt().signum() != 0) {
        if (!tax.save(line.get_TrxName()))
          return false; 
      } else if (!tax.is_new() && !tax.delete(false, line.get_TrxName())) {
        return false;
      } 
    } 
//    tax.save();
    return true;
  }
  
  public boolean recalculateTax(MTaxProvider provider, MOrderLine line, boolean newRecord) {
    if (!newRecord && line.is_ValueChanged("C_Tax_ID") && !line.getParent().isProcessed())
      if (!line.updateOrderTax(true))
        return false;  
    return line.updateHeaderTax();
  }
  
  public boolean updateHeaderTax(MTaxProvider provider, MOrderLine line) {
    String sql = "UPDATE C_Order i SET TotalLines=(SELECT COALESCE(SUM(LineNetAmt),0) FROM C_OrderLine il WHERE i.C_Order_ID=il.C_Order_ID) WHERE C_Order_ID=" + 
      
      line.getC_Order_ID();
    int no = DB.executeUpdate(sql, line.get_TrxName());
    if (no != 1)
      this.log.warning("(1) #" + no); 
    if (line.isTaxIncluded()) {
      sql = "UPDATE C_Order i  SET GrandTotal=TotalLines WHERE C_Order_ID=" + 
        
        line.getC_Order_ID();
    } else {
      sql = "UPDATE C_Order i  SET GrandTotal=TotalLines+(SELECT COALESCE(SUM(TaxAmt),0) FROM C_OrderTax it WHERE i.C_Order_ID=it.C_Order_ID) WHERE C_Order_ID=" + 
        
        line.getC_Order_ID();
    } 
    no = DB.executeUpdate(sql, line.get_TrxName());
    if (no != 1)
      this.log.warning("(2) #" + no); 
    line.clearParent();
    return (no == 1);
  }
  
  public boolean calculateInvoiceTaxTotal(MTaxProvider provider, MInvoice invoice) {
    BigDecimal totalLines = Env.ZERO;
    ArrayList<Integer> taxList = new ArrayList<>();
    MInvoiceLine[] lines = invoice.getLines();
    for (int i = 0; i < lines.length; i++) {
      MInvoiceLine line = lines[i];
      totalLines = totalLines.add(line.getLineNetAmt());
      Integer taxID = Integer.valueOf(line.getC_Tax_ID());
      if (!taxList.contains(taxID)) {
        MTax tax = new MTax(invoice.getCtx(), taxID.intValue(), invoice.get_TrxName());
        MInvoiceTax oTax = MInvoiceTax.get(line, invoice.getPrecision(), false, invoice.get_TrxName());
        oTax.setIsTaxIncluded(invoice.isTaxIncluded());
        if (!calculateTaxFromLines(tax.isDocumentLevel(), tax, line, oTax))
          return false; 
        if (!oTax.save(invoice.get_TrxName()))
          return false; 
        taxList.add(taxID);
      } 
    } 
    BigDecimal grandTotal = totalLines;
    MInvoiceTax[] taxes = invoice.getTaxes(true);
    for (int j = 0; j < taxes.length; j++) {
      MInvoiceTax oTax = taxes[j];
      if (oTax.getC_TaxProvider_ID() != 0) {
        if (!invoice.isTaxIncluded())
          grandTotal = grandTotal.add(oTax.getTaxAmt()); 
      } else {
        MTax tax = MTax.get(Env.getCtx(), oTax.getC_Tax_ID());
        if (tax.isSummary()) {
          MTax[] cTaxes = tax.getChildTaxes(false);
          for (int k = 0; k < cTaxes.length; k++) {
            MTax cTax = cTaxes[k];
            BigDecimal taxAmt = calculateTax(cTax, oTax.getTaxBaseAmt(), false, invoice.getPrecision());
//            BigDecimal taxAmt = cTax.calculateTax(oTax.getTaxBaseAmt(), false, invoice.getPrecision());
            MInvoiceTax newOTax = new MInvoiceTax(invoice.getCtx(), 0, invoice.get_TrxName());
            newOTax.setAD_Org_ID(invoice.getAD_Org_ID());
            newOTax.setC_Invoice_ID(invoice.getC_Invoice_ID());
            newOTax.setC_Tax_ID(cTax.getC_Tax_ID());
            newOTax.setIsTaxIncluded(invoice.isTaxIncluded());
            newOTax.setTaxBaseAmt(oTax.getTaxBaseAmt());
            newOTax.setTaxAmt(taxAmt);
            if (!newOTax.save(invoice.get_TrxName()))
              return false; 
            if (!invoice.isTaxIncluded())
              grandTotal = grandTotal.add(taxAmt); 
          } 
          if (!oTax.delete(true, invoice.get_TrxName()))
            return false; 
//          if (!oTax.save(invoice.get_TrxName()))
//            return false; 
        } else if (!invoice.isTaxIncluded()) {
          grandTotal = grandTotal.add(oTax.getTaxAmt());
        } 
      } 
    } 
    invoice.setTotalLines(totalLines);
    invoice.setGrandTotal(grandTotal);
    return true;
  }
  
  public boolean updateInvoiceTax(MTaxProvider provider, MInvoiceLine line) {
    MTax mtax = new MTax(line.getCtx(), line.getC_Tax_ID(), line.get_TrxName());
    MInvoiceTax tax = MInvoiceTax.get(line, line.getPrecision(), false, line.get_TrxName());
    if (tax != null) {
      if (!calculateTaxFromLines(mtax.isDocumentLevel(), mtax, line, tax))
        return false; 
      if (tax.getTaxAmt().signum() != 0) {
        if (!tax.save(line.get_TrxName()))
          return false; 
      } else if (!tax.is_new() && !tax.delete(false, line.get_TrxName())) {
        return false;
      } 
    } 
//    tax.save(line.get_TrxName());
    return true;
  }
  
  public boolean recalculateTax(MTaxProvider provider, MInvoiceLine line, boolean newRecord) {
    if (!newRecord && line.is_ValueChanged("C_Tax_ID") && !line.getParent().isProcessed()) {
      MTax mtax = new MTax(line.getCtx(), line.getC_Tax_ID(), line.get_TrxName());
      MInvoiceTax tax = MInvoiceTax.get(line, line.getPrecision(), true, line.get_TrxName());
      if (tax != null) {
        if (!calculateTaxFromLines(mtax.isDocumentLevel(), mtax, line, tax))
          return false; 
        if (tax.getTaxAmt().signum() != 0) {
          if (!tax.save(line.get_TrxName()))
            return false; 
        } else if (!tax.is_new() && !tax.delete(false, line.get_TrxName())) {
          return false;
        } 
      } 
    } 
    return line.updateHeaderTax();
  }
  
  public boolean updateHeaderTax(MTaxProvider provider, MInvoiceLine line) {
    String sql = "UPDATE C_Invoice i SET TotalLines=(SELECT COALESCE(SUM(LineNetAmt),0) FROM C_InvoiceLine il WHERE i.C_Invoice_ID=il.C_Invoice_ID) WHERE C_Invoice_ID=" + 
      
      line.getC_Invoice_ID();
    int no = DB.executeUpdate(sql, line.get_TrxName());
    if (no != 1)
      this.log.warning("(1) #" + no); 
    if (line.isTaxIncluded()) {
      sql = "UPDATE C_Invoice i  SET GrandTotal=TotalLines WHERE C_Invoice_ID=" + 
        
        line.getC_Invoice_ID();
    } else {
      sql = "UPDATE C_Invoice i  SET GrandTotal=TotalLines+(SELECT COALESCE(SUM(TaxAmt),0) FROM C_InvoiceTax it WHERE i.C_Invoice_ID=it.C_Invoice_ID) WHERE C_Invoice_ID=" + 
        
        line.getC_Invoice_ID();
    } 
    no = DB.executeUpdate(sql, line.get_TrxName());
    if (no != 1)
      this.log.warning("(2) #" + no); 
    line.clearParent();
    return (no == 1);
  }
  
  public boolean calculateRMATaxTotal(MTaxProvider provider, MRMA rma) {
    return false;
  }
  
  public boolean updateRMATax(MTaxProvider provider, MRMALine line) {
    return false;
  }
  
  public boolean recalculateTax(MTaxProvider provider, MRMALine line, boolean newRecord) {
    return false;
  }
  
  public boolean updateHeaderTax(MTaxProvider provider, MRMALine line) {
    return false;
  }
  
  public String validateConnection(MTaxProvider provider, ProcessInfo pi) throws Exception {
    return null;
  }
}
