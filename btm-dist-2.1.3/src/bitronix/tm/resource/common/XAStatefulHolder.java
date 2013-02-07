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
package bitronix.tm.resource.common;

import java.util.List;
import java.util.Date;

/**
 * Any pooled connection class must implement the {@link XAStatefulHolder} interface. It defines all the services
 * that must be implemented by the connection as well as the pooling lifecycle states.
 * Instances of this interface have to create and manage {@link XAResourceHolder}s.
 *
 * @author lorban
 */
public interface XAStatefulHolder {

    /**
     * The state in which the resource is when it is closed and unusable.
     */
    public final static int STATE_CLOSED = 0;

    /**
     * The state in which the resource is when it is available in the pool.
     */
    public final static int STATE_IN_POOL = 1;

    /**
     * The state in which the resource is when it out of the pool but accessible by the application.
     */
    public final static int STATE_ACCESSIBLE = 2;

    /**
     * The state in which the resource is when it out of the pool but not accessible by the application.
     */
    public final static int STATE_NOT_ACCESSIBLE = 3;


    /**
     * Get the current resource state.
     * <p>This method is thread-safe.</p>
     * @return the current resource state.
     */
    public int getState();

    /**
     * Set the current resource state.
     * <p>This method is thread-safe.</p>
     * @param state the current resource state.
     */
    public void setState(int state);

    /**
     * Register an implementation of {@link StateChangeListener}.
     * @param listener the {@link StateChangeListener} implementation to register.
     */
    public void addStateChangeEventListener(StateChangeListener listener);

    /**
     * Unregister an implementation of {@link StateChangeListener}.
     * @param listener the {@link StateChangeListener} implementation to unregister.
     */
    public void removeStateChangeEventListener(StateChangeListener listener);

    /**
     * Get the list of {@link bitronix.tm.resource.common.XAResourceHolder}s created by this
     * {@link bitronix.tm.resource.common.XAStatefulHolder} that are still open.
     * <p>This method is thread-safe.</p>
     * @return the list of {@link XAResourceHolder}s created by this
     *         {@link bitronix.tm.resource.common.XAStatefulHolder} that are still open.
     */
    public List<XAResourceHolder> getXAResourceHolders();

    /**
     * Create a disposable handler used to drive a pooled instance of
     * {@link bitronix.tm.resource.common.XAStatefulHolder}.
     * <p>This method is thread-safe.</p>
     * @return a resource-specific disaposable connection object.
     * @throws Exception a resource-specific exception thrown when the disaposable connection cannot be created.
     */
    public Object getConnectionHandle() throws Exception;

    /**
     * Close the physical connection that this {@link bitronix.tm.resource.common.XAStatefulHolder} represents.
     * @throws Exception a resource-specific exception thrown when there is an error closing the physical connection.
     */
    public void close() throws Exception;

    /**
     * Get the date at which this object was last released to the pool. This is required to check if it is eligible
     * for discard when the containing pool needs to shrink.
     * @return the date at which this object was last released to the pool or null if it never left the pool.
     */
    public Date getLastReleaseDate();

}
