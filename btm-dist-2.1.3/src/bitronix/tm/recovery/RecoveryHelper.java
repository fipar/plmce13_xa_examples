/*
 * Bitronix Transaction Manager
 *
 * Copyright (c) 2010, Bitronix Software.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA 02110-1301 USA
 */
package bitronix.tm.recovery;

import bitronix.tm.internal.XAResourceHolderState;
import bitronix.tm.BitronixXid;
import bitronix.tm.TransactionManagerServices;
import bitronix.tm.utils.Uid;
import bitronix.tm.utils.Decoder;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.util.Collections;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reovery helper methods.
 *
 * @author lorban
 */
public class RecoveryHelper {

    private final static Logger log = LoggerFactory.getLogger(RecoveryHelper.class);

    /**
     * Run the recovery process on the target resource.
     * @return a Set of BitronixXids.
     * @param xaResourceHolderState the {@link XAResourceHolderState} to recover.
     * @throws javax.transaction.xa.XAException if {@link XAResource#recover(int)} calls fail.
     */
    public static Set<BitronixXid> recover(XAResourceHolderState xaResourceHolderState) throws XAException {
        Set<BitronixXid> xids = new HashSet<BitronixXid>();

        if (log.isDebugEnabled()) log.debug("recovering with STARTRSCAN");
        int xidCount;
        try {
            xidCount = recover(xaResourceHolderState, xids, XAResource.TMSTARTRSCAN);
        } catch (XAException ex) {
            if (xaResourceHolderState.getIgnoreRecoveryFailures()) {
                if (log.isDebugEnabled()) log.debug("ignoring recovery failure on resource " + xaResourceHolderState, ex);
                return Collections.emptySet();
            }
            throw ex;
        }
        if (log.isDebugEnabled()) log.debug("STARTRSCAN recovered " + xidCount + " xid(s) on " + xaResourceHolderState);

        try {
            while (xidCount > 0) {
                if (log.isDebugEnabled()) log.debug("recovering with NOFLAGS");
                xidCount = recover(xaResourceHolderState, xids, XAResource.TMNOFLAGS);
                if (log.isDebugEnabled()) log.debug("NOFLAGS recovered " + xidCount + " xid(s) on " + xaResourceHolderState);
            }
        } catch (XAException ex) {
            if (log.isDebugEnabled()) log.debug("NOFLAGS recovery call failed", ex);
        }

        try {
            if (log.isDebugEnabled()) log.debug("recovering with ENDRSCAN");
            xidCount = recover(xaResourceHolderState, xids, XAResource.TMENDRSCAN);
            if (log.isDebugEnabled()) log.debug("ENDRSCAN recovered " + xidCount + " xid(s) on " + xaResourceHolderState);
        } catch (XAException ex) {
            if (log.isDebugEnabled()) log.debug("ENDRSCAN recovery call failed", ex);
        }

        return xids;
    }

    /**
     * Call {@link XAResource#recover(int)} on the resource and fill the <code>alreadyRecoveredXids</code> Set
     * with recovered {@link BitronixXid}s.
     * Step 1.
     * @return the amount of recovered {@link Xid}.
     * @param resourceHolderState the {@link XAResourceHolderState} to recover.
     * @param alreadyRecoveredXids a set of {@link Xid}s already recovered from this resource in this recovery session.
     * @param flags any combination of {@link XAResource#TMSTARTRSCAN}, {@link XAResource#TMNOFLAGS} or {@link XAResource#TMENDRSCAN}.
     * @throws javax.transaction.xa.XAException if {@link XAResource#recover(int)} call fails.
     */
    private static int recover(XAResourceHolderState resourceHolderState, Set<BitronixXid> alreadyRecoveredXids, int flags) throws XAException {
        Xid[] xids = resourceHolderState.getXAResource().recover(flags);
        if (xids == null)
            return 0;

        boolean currentNodeOnly = TransactionManagerServices.getConfiguration().isCurrentNodeOnlyRecovery();

        Set<BitronixXid> freshlyRecoveredXids = new HashSet<BitronixXid>();
        for (Xid xid : xids) {
            if (xid.getFormatId() != BitronixXid.FORMAT_ID) {
                if (log.isDebugEnabled()) log.debug("skipping non-bitronix XID " + xid + "(format ID: " + xid.getFormatId() +
                     " GTRID: " + new Uid(xid.getGlobalTransactionId()) + "BQUAL: " + new Uid(xid.getBranchQualifier()) + ")");
                continue;
            }

            BitronixXid bitronixXid = new BitronixXid(xid);

            if (currentNodeOnly) {
                if (log.isDebugEnabled()) log.debug("recovering XIDs generated by this node only - recovered XIDs' GTRID must contain this JVM uniqueId");
                byte[] extractedServerId = bitronixXid.getGlobalTransactionIdUid().extractServerId();
                byte[] jvmUniqueId = TransactionManagerServices.getConfiguration().buildServerIdArray();

                if (extractedServerId == null) {
                    log.error("skipping XID " + bitronixXid + " as its GTRID's serverId is null. It looks like the disk journal is corrupted!");
                    continue;
                }

                if (!Arrays.equals(jvmUniqueId, extractedServerId)) {
                    String extractedServerIdString = new String(extractedServerId);
                    String jvmUniqueIdString = new String(jvmUniqueId);

                    if (log.isDebugEnabled()) log.debug("skipping XID " + bitronixXid + " as its GTRID's serverId <" + extractedServerIdString + "> does not match this JVM unique ID <" + jvmUniqueIdString + ">");
                    continue;
                }
            } else {
                if (log.isDebugEnabled()) log.debug("recovering all XIDs regardless of this JVM uniqueId");
            }

            if (alreadyRecoveredXids.contains(bitronixXid)) {
                if (log.isDebugEnabled()) log.debug("already recovered XID " + bitronixXid + ", skipping it");
                continue;
            }

            if (freshlyRecoveredXids.contains(bitronixXid)) {
                log.warn("resource " + resourceHolderState.getUniqueName() + " recovered two identical XIDs within the same recover call: " + bitronixXid);
                continue;
            }

            if (log.isDebugEnabled()) log.debug("recovered " + bitronixXid);
            freshlyRecoveredXids.add(bitronixXid);
        } // for i < xids.length

        alreadyRecoveredXids.addAll(freshlyRecoveredXids);
        return freshlyRecoveredXids.size();
    }


    /**
     * Commit the specified branch of a dangling transaction.
     * @param xaResourceHolderState the {@link XAResourceHolderState} to commit the branch on.
     * @param xid the {@link Xid} to commit.
     * @return true when commit was successful.
     */
    public static boolean commit(XAResourceHolderState xaResourceHolderState, Xid xid) {
        String uniqueName = xaResourceHolderState.getUniqueName();
        boolean success = true;
        boolean forget = false;

        try {
            xaResourceHolderState.getXAResource().commit(xid, false);
        } catch (XAException ex) {
            String extraErrorDetails = TransactionManagerServices.getExceptionAnalyzer().extractExtraXAExceptionDetails(ex);
            if (ex.errorCode == XAException.XAER_NOTA) {
                log.error("unable to commit in-doubt branch on resource " + uniqueName + " - error=XAER_NOTA" +
                        (extraErrorDetails == null ? "" : ", extra error=" + extraErrorDetails) + ". Forgotten heuristic?", ex);
            }
            else if (ex.errorCode == XAException.XA_HEURCOM) {
                log.info("unable to commit in-doubt branch on resource " + uniqueName + " - error=" +
                        Decoder.decodeXAExceptionErrorCode(ex) + (extraErrorDetails == null ? "" : ", extra error=" + extraErrorDetails) +
                        ". Heuristic decision compatible with the global state of this transaction.");
                forget = true;
            }
            else if (ex.errorCode == XAException.XA_HEURHAZ || ex.errorCode == XAException.XA_HEURMIX || ex.errorCode == XAException.XA_HEURRB) {
                log.error("unable to commit in-doubt branch on resource " + uniqueName + " - error=" +
                        Decoder.decodeXAExceptionErrorCode(ex) + (extraErrorDetails == null ? "" : ", extra error=" + extraErrorDetails) +
                        ". Heuristic decision incompatible with the global state of this transaction!");
                forget = true;
                success = false;
            }
            else {
                log.error("unable to commit in-doubt branch on resource " + uniqueName +
                        " - error=" + Decoder.decodeXAExceptionErrorCode(ex) + (extraErrorDetails == null ? "" : ", extra error=" + extraErrorDetails) + ".", ex);
                success = false;
            }
        }
        if (forget) {
            try {
                if (log.isDebugEnabled()) log.debug("forgetting XID " + xid + " on resource " + uniqueName);
                xaResourceHolderState.getXAResource().forget(xid);
            } catch (XAException ex) {
                String extraErrorDetails = TransactionManagerServices.getExceptionAnalyzer().extractExtraXAExceptionDetails(ex);
                log.error("unable to forget XID " + xid + " on resource " + uniqueName + ", error=" + Decoder.decodeXAExceptionErrorCode(ex) +
                        (extraErrorDetails == null ? "" : ", extra error=" + extraErrorDetails), ex);
            }
        }
        return success;
    }

    /**
     * Rollback the specified branch of a dangling transaction.
     * @param xaResourceHolderState the {@link XAResourceHolderState} to rollback the branch on.
     * @param xid the {@link Xid} to rollback.
     * @return true when rollback was successful.
     */
    public static boolean rollback(XAResourceHolderState xaResourceHolderState, Xid xid) {
        String uniqueName = xaResourceHolderState.getUniqueName();
        boolean success = true;
        boolean forget = false;
        try {
            xaResourceHolderState.getXAResource().rollback(xid);
        } catch (XAException ex) {
            String extraErrorDetails = TransactionManagerServices.getExceptionAnalyzer().extractExtraXAExceptionDetails(ex);
            if (ex.errorCode == XAException.XAER_NOTA) {
                log.error("unable to rollback aborted in-doubt branch on resource " + uniqueName + " - error=XAER_NOTA" +
                        (extraErrorDetails == null ? "" : ", extra error=" + extraErrorDetails) + ". Forgotten heuristic?", ex);
            }
            else if (ex.errorCode == XAException.XA_HEURRB) {
                log.info("unable to rollback aborted in-doubt branch on resource " + uniqueName + " - error=" +
                        Decoder.decodeXAExceptionErrorCode(ex) + (extraErrorDetails == null ? "" : ", extra error=" + extraErrorDetails) +
                        ". Heuristic decision compatible with the global state of this transaction.");
                forget = true;
            }
            else if (ex.errorCode == XAException.XA_HEURHAZ || ex.errorCode == XAException.XA_HEURMIX || ex.errorCode == XAException.XA_HEURCOM) {
                log.error("unable to rollback aborted in-doubt branch on resource " + uniqueName + " - error=" +
                        Decoder.decodeXAExceptionErrorCode(ex) + (extraErrorDetails == null ? "" : ", extra error=" + extraErrorDetails) +
                        ". Heuristic decision incompatible with the global state of this transaction!");
                forget = true;
                success = false;
            }
            else {
                log.error("unable to rollback aborted in-doubt branch on resource " + uniqueName + " - error=" +
                        Decoder.decodeXAExceptionErrorCode(ex) + (extraErrorDetails == null ? "" : ", extra error=" + extraErrorDetails) + ".", ex);
                success = false;
            }
        }
        if (forget) {
            try {
                if (log.isDebugEnabled()) log.debug("forgetting XID " + xid + " on resource " + uniqueName);
                xaResourceHolderState.getXAResource().forget(xid);
            } catch (XAException ex) {
                String extraErrorDetails = TransactionManagerServices.getExceptionAnalyzer().extractExtraXAExceptionDetails(ex);
                log.error("unable to forget XID " + xid + " on resource " + uniqueName + ", error=" + Decoder.decodeXAExceptionErrorCode(ex) +
                        (extraErrorDetails == null ? "" : ", extra error=" + extraErrorDetails), ex);
            }
        }
        return success;
    }


}
