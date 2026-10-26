package com.enterprise.iam.common.transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Reflection-safe utility for interacting with Seata runtime context without hard compile-time
 * or runtime dependency on io.seata.* classes.
 */
public final class SeataTransactionSupport {

    private static final Logger log = LoggerFactory.getLogger(SeataTransactionSupport.class);
    private static final String ROOT_CONTEXT_CLASS = "io.seata.core.context.RootContext";

    private static final boolean SEATA_PRESENT;
    private static final Method GET_XID_METHOD;
    private static final Method IN_GLOBAL_TRANSACTION_METHOD;
    private static final Method BIND_METHOD;
    private static final Method UNBIND_METHOD;

    static {
        boolean present = false;
        Method getXid = null;
        Method inGlobal = null;
        Method bind = null;
        Method unbind = null;
        try {
            Class<?> rootContextClass = Class.forName(ROOT_CONTEXT_CLASS);
            getXid = rootContextClass.getMethod("getXID");
            inGlobal = rootContextClass.getMethod("inGlobalTransaction");
            bind = rootContextClass.getMethod("bind", String.class);
            unbind = rootContextClass.getMethod("unbind");
            present = true;
        } catch (Throwable ignored) {
            // Seata not present on classpath
        }
        SEATA_PRESENT = present;
        GET_XID_METHOD = getXid;
        IN_GLOBAL_TRANSACTION_METHOD = inGlobal;
        BIND_METHOD = bind;
        UNBIND_METHOD = unbind;
    }

    private SeataTransactionSupport() {
    }

    public static boolean isSeataPresent() {
        return SEATA_PRESENT;
    }

    public static boolean inGlobalTransaction() {
        if (!SEATA_PRESENT || IN_GLOBAL_TRANSACTION_METHOD == null) {
            return false;
        }
        try {
            Object result = IN_GLOBAL_TRANSACTION_METHOD.invoke(null);
            return result instanceof Boolean b && b;
        } catch (ReflectiveOperationException e) {
            log.trace("Failed to inspect Seata global transaction state: {}", e.getMessage());
            return false;
        }
    }

    public static String getXid() {
        if (!SEATA_PRESENT || GET_XID_METHOD == null) {
            return null;
        }
        try {
            Object result = GET_XID_METHOD.invoke(null);
            return result != null ? result.toString() : null;
        } catch (ReflectiveOperationException e) {
            log.trace("Failed to obtain Seata XID: {}", e.getMessage());
            return null;
        }
    }

    public static void bindXid(String xid) {
        if (SEATA_PRESENT && BIND_METHOD != null && xid != null) {
            try {
                BIND_METHOD.invoke(null, xid);
            } catch (ReflectiveOperationException e) {
                log.warn("Failed to bind Seata XID: {}", e.getMessage());
            }
        }
    }

    public static String unbindXid() {
        if (SEATA_PRESENT && UNBIND_METHOD != null) {
            try {
                Object result = UNBIND_METHOD.invoke(null);
                return result != null ? result.toString() : null;
            } catch (ReflectiveOperationException e) {
                log.warn("Failed to unbind Seata XID: {}", e.getMessage());
            }
        }
        return null;
    }
}
