package com.idlogix.pfc.promotion.factories;

import java.util.ArrayList;
import java.util.List;

import org.adempiere.base.IColumnCallout;
import org.adempiere.base.IColumnCalloutFactory;

import com.idlogix.pfc.promotion.callouts.AddPromotion;

public class CalloutFactory implements IColumnCalloutFactory {

	@Override
	public IColumnCallout[] getColumnCallouts(String tableName, String columnName) {
		// TODO Auto-generated method stub
		List<IColumnCallout> list = new ArrayList<IColumnCallout>();
		
		if(tableName.equalsIgnoreCase("C_Invoice") && columnName.equalsIgnoreCase("calc_promotion"))
			list.add(new AddPromotion());
		return list!= null ? list.toArray(new IColumnCallout[0]): new IColumnCallout[0];
		
		
	}

}
