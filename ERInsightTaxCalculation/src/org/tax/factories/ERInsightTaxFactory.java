package org.tax.factories;

import org.adempiere.base.ITaxProviderFactory;
import org.adempiere.model.ITaxProvider;
import org.tax.providers.ERInsightTaxProvider;

public class ERInsightTaxFactory implements ITaxProviderFactory {
  public ITaxProvider newTaxProviderInstance(String className) {
    return (ITaxProvider)new ERInsightTaxProvider();
  }
}
