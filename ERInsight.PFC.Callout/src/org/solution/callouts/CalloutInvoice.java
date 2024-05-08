package org.solution.callouts;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Properties;

import org.adempiere.base.IColumnCallout;
import org.compiere.model.GridField;
import org.compiere.model.GridTab;
import org.compiere.model.MInvoice;
import org.compiere.util.DB;
import org.compiere.util.Env;

public class CalloutInvoice implements IColumnCallout {

	@Override
	public String start(Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value, Object oldValue) {
		// TODO Auto-generated method stub
		
		int invoice_id = (int)mTab.getValue("C_Invoice_ID");
		MInvoice invoice = new MInvoice(Env.getCtx(), invoice_id, (String)null);
		if(invoice.isSOTrx()) {
			int M_Product_ID = mTab.getValue("M_Product_ID")==null?-1:(int)mTab.getValue("M_Product_ID");
			if(M_Product_ID>0) {
			Timestamp invoiceDate = (Timestamp)invoice.getDateInvoiced();
			int M_PriceList_ID = (int)invoice.getM_PriceList_ID();
			int M_PriceList_Version_ID = Env.getContextAsInt(ctx, WindowNo, "M_PriceList_Version_ID");
		    if (M_PriceList_Version_ID == 0 && M_PriceList_ID > 0) {
		      String str = "SELECT plv.M_PriceList_Version_ID FROM M_PriceList_Version plv WHERE plv.M_PriceList_ID=?  AND plv.ValidFrom <= ? ORDER BY plv.ValidFrom DESC";
		      M_PriceList_Version_ID = DB.getSQLValueEx(null, str, new Object[] { Integer.valueOf(M_PriceList_ID), invoiceDate });
		      if (M_PriceList_Version_ID > 0)
		        Env.setContext(ctx, WindowNo, "M_PriceList_Version_ID", M_PriceList_Version_ID); 
		    } 
		    String sql = "select coalesce(MRP, 0) from M_ProductPrice where m_product_id=" + M_Product_ID + " and M_PriceList_Version_ID = " + M_PriceList_Version_ID;
		    BigDecimal mrp = DB.getSQLValueBD(null, sql, new Object[0]);
		    mTab.setValue("MRP", mrp);
			}
		}
		
		
		
		return null;
	}

}
