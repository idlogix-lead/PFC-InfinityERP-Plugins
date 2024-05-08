package org.pfc.validators;

import java.math.BigDecimal;

import org.compiere.model.MClient;
import org.compiere.model.MInvoice;
import org.compiere.model.MInvoiceLine;
import org.compiere.model.MOrderLine;
import org.compiere.model.ModelValidationEngine;
import org.compiere.model.ModelValidator;
import org.compiere.model.PO;
import org.compiere.util.Env;

public class InvoiceLineValidator implements ModelValidator {

	@Override
	public void initialize(ModelValidationEngine engine, MClient client) {
		// TODO Auto-generated method stub
		engine.addModelChange(MInvoiceLine.Table_Name, this);
	}

	@Override
	public int getAD_Client_ID() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public String login(int AD_Org_ID, int AD_Role_ID, int AD_User_ID) {
		// TODO Auto-generated method stub
		System.out.println("Logged In !!!!!!!!!!!!!!!");
		return null;
	}

	@Override
	public String modelChange(PO po, int type) throws Exception {
		// TODO Auto-generated method stub
		if(po.get_Table_ID()==MInvoiceLine.Table_ID && type==TYPE_BEFORE_NEW) {
			int orderline_id = po.get_ValueAsInt("C_OrderLine_ID");
			int invoice_id = po.get_ValueAsInt("C_Invoice_ID");
			MInvoice invoice = new MInvoice(Env.getCtx(), invoice_id, null);
			if(!invoice.isSOTrx())
				return "";
			MOrderLine ol = new MOrderLine(Env.getCtx(), orderline_id, null);
			BigDecimal mrp = (BigDecimal)ol.get_Value("MRP");
			if(ol!=null)
			    po.set_ValueOfColumn("MRP", mrp);
		}
		return null;
	}

	@Override
	public String docValidate(PO po, int timing) {
		// TODO Auto-generated method stub
		return null;
	}

}
