package org.pfc.factories;

import org.adempiere.base.IModelValidatorFactory;
import org.compiere.model.ModelValidator;
import org.pfc.validators.InvoiceLineValidator;

public class ModelValidatorFactory implements IModelValidatorFactory {

	@Override
	public ModelValidator newModelValidatorInstance(String className) {
		// TODO Auto-generated method stub
		if(className.equalsIgnoreCase("org.pfc.validators.InvoiceLineValidator"))
			return new InvoiceLineValidator();
		return null;
	}

}
