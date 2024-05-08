package ERInsight.Callouts;

import java.lang.reflect.Method;
import java.util.logging.Level;
import org.adempiere.base.ICalloutFactory;
import org.adempiere.base.equinox.EquinoxExtensionLocator;
import org.compiere.model.Callout;
import org.compiere.util.CLogger;

public class erinsightcalloutfactory implements ICalloutFactory {
  private static final CLogger log = CLogger.getCLogger(erinsightcalloutfactory.class);
  
  public Callout getCallout(String className, String methodName) {
    Callout callout = null;
    String strClassName = className.replace("org.compiere.model", "org.solution.callouts");
    callout = (Callout)EquinoxExtensionLocator.instance().locate(Callout.class, Callout.class.getName(), strClassName, null).getExtension();
    if (callout == null) {
      Class<?> calloutClass = null;
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      if (classLoader != null)
        try {
          calloutClass = classLoader.loadClass(strClassName);
        } catch (ClassNotFoundException ex) {
          if (log.isLoggable(Level.FINE))
            log.log(Level.FINE, strClassName, ex); 
        }  
      if (calloutClass == null) {
        classLoader = getClass().getClassLoader();
        try {
          calloutClass = classLoader.loadClass(strClassName);
        } catch (ClassNotFoundException ex) {
          log.log(Level.WARNING, strClassName, ex);
          return null;
        } 
      } 
      if (calloutClass == null)
        return null; 
      try {
        callout = (Callout)calloutClass.newInstance();
      } catch (Exception ex) {
        log.log(Level.WARNING, "Instance for " + strClassName, ex);
        return null;
      } 
      Method[] methods = calloutClass.getDeclaredMethods();
      for (int i = 0; i < methods.length; i++) {
        if (methods[i].getName().equals(methodName))
          return callout; 
      } 
    } 
    log.log(Level.FINE, "Required method " + methodName + " not found in class " + className);
    return null;
  }
}
