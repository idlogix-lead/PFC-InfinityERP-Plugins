package com.idlogix.pfc.promotion.callouts;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.adempiere.base.IColumnCallout;
import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.GridField;
import org.compiere.model.GridTab;
import org.compiere.model.MBPGroup;
import org.compiere.model.MBPartner;
import org.compiere.model.MInvoice;
import org.compiere.model.MOrder;
import org.compiere.model.MOrderLine;
import org.compiere.model.Query;
import org.compiere.util.DB;
import org.compiere.util.Env;
import com.idlogix.pfc.promotion.models.Promotion;
import org.compiere.model.MTax;
import org.compiere.model.MInvoice;
import org.compiere.model.MInvoiceLine;

public class AddPromotion  implements IColumnCallout{

	MInvoice invoice;
	MBPartner bp;
	int c_charge_id;
	
	@Override
	public String start(Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value, Object oldValue) {
		// TODO Auto-generated method stub
		
		int invoice_id = Integer.parseInt(mTab.get_ValueAsString("C_Invoice_ID"));
		invoice = new MInvoice(ctx,invoice_id,null);
		bp  = new MBPartner(Env.getCtx(),invoice.getC_BPartner_ID(),null);
		
		
		
		Query query = new Query(Env.getCtx(), MInvoiceLine.Table_Name, " c_invoice_id = ? and idl_promotionline_id is not null ", null);
		query.setParameters(new Object[] { invoice_id });
		List<MInvoiceLine> old_lines = query.list();
		for(MInvoiceLine line:old_lines) {
			line.delete(true);
		}
		
		
		MInvoiceLine[] lines = invoice.getLines();
		for(MInvoiceLine line:lines) {
			if(line.getM_Product_ID() !=0) {
				MTax tax = new MTax(Env.getCtx(),line.getC_Tax_ID(),null);
				BigDecimal taxrate = tax.getRate();
				List<Promotion> prom_list = this.getPromotions(line.getM_Product_ID(),line.getQtyEntered());
				if(prom_list.size()>0) {
					for(Promotion prom:prom_list) {
						MInvoiceLine il = new MInvoiceLine(Env.getCtx(),0,null);
						il.setAD_Org_ID(invoice.getAD_Org_ID());
						il.setInvoice(invoice);
						il.setC_Invoice_ID(invoice.getC_Invoice_ID());
						il.setC_Charge_ID(prom.getC_carge_id());
						il.setQty(new BigDecimal(1));
						il.setQtyInvoiced(new BigDecimal(1));
						il.setPriceEntered(prom.getAmount().negate());
						il.setPriceActual(prom.getAmount().negate());
						il.setLineNetAmt(prom.getAmount().negate());
						if(line.getAD_Org_ID()!=1000000) {
						il.setC_Tax_ID(line.getC_Tax_ID());
						}
						if(taxrate.compareTo(BigDecimal.ZERO)>1) {
							BigDecimal taxamount = taxrate.divide(new BigDecimal(100),RoundingMode.DOWN).multiply(prom.getAmount()).setScale(0, RoundingMode.FLOOR);
							taxamount = taxamount.add(prom.getAmount());
							il.setPriceActual(taxamount.negate());
						}
						il.set_ValueOfColumn("IDL_PromotionLine_ID",prom.getIdl_promotionline_id());
						il.set_ValueOfColumn("prom_prod_id", line.getM_Product_ID());
						il.save();	
						int a = 0;
					}
					
				}
			}
		}
		mTab.dataRefresh();
		return null;
	}
	


private  List<Promotion> getPromotions(int id, BigDecimal qty) {
	
	List<Promotion> promotions = new ArrayList<Promotion>();
	BigDecimal prom_amt = new BigDecimal(0);
	String strSQL = " select IDL_Promotionline_id,minqty,amount,p.c_charge_id,m_product_id \n"
		      + "from adempiere.IDL_Promotion p \n"
			  + "join adempiere.IDL_Promotionline pl ON p.idl_promotion_id = pl.idl_promotion_id \n"
	      	  + "where m_product_id  = "+ id +" \n"
	      	  +" and '"+ invoice.getDateInvoiced() +"'::date between startdate::date and enddate::date and minqty<> 0 and amount<> 0 and  minqty <=" + qty +" \n"
	      			 +"and C_BP_Group_ID = "+bp.getC_BP_Group_ID()+" \n";
	PreparedStatement pstmt = null;
	ResultSet rs = null;
	
	try
	{
		pstmt = DB.prepareStatement (strSQL.toString(), null);
		rs = pstmt.executeQuery ();
		
		while (rs.next ())	
		{	
			BigDecimal sets = qty.divide(rs.getBigDecimal("minqty"),RoundingMode.DOWN).setScale(0, RoundingMode.FLOOR);
			prom_amt = prom_amt.add(sets.multiply(rs.getBigDecimal("amount")));
			Promotion promotion =new Promotion();
			promotion.setAmount(prom_amt);
			promotion.setC_carge_id(rs.getInt("C_Charge_ID"));
			promotion.setM_product_id(rs.getInt("m_product_id"));
			promotion.setIdl_promotionline_id(rs.getInt("idl_promotionline_id"));
			promotions.add(promotion);
				
		}
	}
	catch (Exception e)
	{
		throw new AdempiereException(e);
	}
	return promotions;
	
}

}
