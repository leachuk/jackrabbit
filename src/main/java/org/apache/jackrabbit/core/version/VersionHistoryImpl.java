/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.core.version;

import org.apache.jackrabbit.core.ItemLifeCycleListener;
import org.apache.jackrabbit.core.ItemManager;
import org.apache.jackrabbit.core.NodeId;
import org.apache.jackrabbit.core.SessionImpl;
import org.apache.jackrabbit.core.NodeImpl;
import org.apache.jackrabbit.core.state.NodeState;
import org.apache.jackrabbit.name.IllegalNameException;
import org.apache.jackrabbit.name.NoPrefixDeclaredException;
import org.apache.jackrabbit.name.QName;
import org.apache.jackrabbit.name.UnknownPrefixException;
import org.apache.jackrabbit.name.NameFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Item;
import javax.jcr.RepositoryException;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.NodeIterator;
import javax.jcr.InvalidItemStateException;
import javax.jcr.nodetype.NodeDefinition;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.version.Version;
import javax.jcr.version.VersionException;
import javax.jcr.version.VersionHistory;
import javax.jcr.version.VersionIterator;

/**
 * Base implementation of the {@link javax.jcr.version.VersionHistory} interface.
 */
public class VersionHistoryImpl extends NodeImpl implements VersionHistory {

    /**
     * Logger instance.
     */
    private static Logger log = LoggerFactory.getLogger(VersionHistoryImpl.class);

    /**
     * Create a new instance of this class.
     * @param itemMgr item manager
     * @param session session
     * @param id node id
     * @param state node state
     * @param definition node definition
     * @param listeners life cycle listeners
     */
    public VersionHistoryImpl(ItemManager itemMgr, SessionImpl session, NodeId id,
                              NodeState state, NodeDefinition definition,
                              ItemLifeCycleListener[] listeners) {
        super(itemMgr, session, id, state, definition, listeners);
    }

    /**
     * Returns the internal version history. Subclass responsibility.
     *
     * @return internal version history
     * @throws RepositoryException if the internal version history is not available
     */
    protected InternalVersionHistory getInternalVersionHistory()
            throws RepositoryException {
        InternalVersionHistory history =
                session.getVersionManager().getVersionHistory((NodeId) id);
        if (history == null) {
            throw new InvalidItemStateException(id + ": the item does not exist anymore");
        }
        return history;
    }

    /**
     * @see javax.jcr.version.VersionHistory#getRootVersion()
     */
    public Version getRootVersion() throws RepositoryException {
        return (Version) session.getNodeById(
                getInternalVersionHistory().getRootVersion().getId());
    }

    /**
     * @see javax.jcr.version.VersionHistory#getAllVersions()
     */
    public VersionIterator getAllVersions() throws RepositoryException {
        return new VersionIteratorImpl(session,
                getInternalVersionHistory().getRootVersion());
    }

    /**
     * @see javax.jcr.version.VersionHistory#getVersion(String)
     */
    public Version getVersion(String versionName)
            throws VersionException, RepositoryException {
        try {
            QName name = NameFormat.parse(versionName, session.getNamespaceResolver());
            InternalVersion v = getInternalVersionHistory().getVersion(name);
            if (v == null) {
                throw new VersionException("No version with name '" + versionName + "' exists in this version history.");
            }
            return (Version) session.getNodeById(v.getId());
        } catch (IllegalNameException e) {
            throw new VersionException(e);
        } catch (UnknownPrefixException e) {
            throw new VersionException(e);
        }
    }

    /**
     * @see javax.jcr.version.VersionHistory#getVersionByLabel(String)
     */
    public Version getVersionByLabel(String label) throws RepositoryException {
        try {
            QName qLabel = NameFormat.parse(label, session.getNamespaceResolver());
            InternalVersion v = getInternalVersionHistory().getVersionByLabel(qLabel);
            if (v == null) {
                throw new VersionException("No version with label '" + label + "' exists in this version history.");
            }
            return (Version) session.getNodeById(v.getId());
        } catch (IllegalNameException e) {
            throw new VersionException(e);
        } catch (UnknownPrefixException e) {
            throw new VersionException(e);
        }
    }

    /**
     * @see javax.jcr.version.VersionHistory#addVersionLabel(String, String, boolean)
     */
    public void addVersionLabel(String versionName, String label, boolean move)
            throws VersionException, RepositoryException {
        try {
            session.getVersionManager().setVersionLabel(this,
                    NameFormat.parse(versionName, session.getNamespaceResolver()),
                    NameFormat.parse(label, session.getNamespaceResolver()),
                    move);
        } catch (IllegalNameException e) {
            throw new VersionException(e);
        } catch (UnknownPrefixException e) {
            throw new VersionException(e);
        }
    }

    /**
     * @see javax.jcr.version.VersionHistory#removeVersionLabel(String)
     */
    public void removeVersionLabel(String label) throws RepositoryException {
        try {
            Version existing = session.getVersionManager().setVersionLabel(this,
                    null,
                    NameFormat.parse(label, session.getNamespaceResolver()),
                    true);
            if (existing == null) {
                throw new VersionException("No version with label '" + label + "' exists in this version history.");
            }
        } catch (IllegalNameException e) {
            throw new VersionException(e);
        } catch (UnknownPrefixException e) {
            throw new VersionException(e);
        }
    }


    /**
     * @see javax.jcr.version.VersionHistory#getVersionLabels
     */
    public String[] getVersionLabels() throws RepositoryException {
        try {
            QName[] labels = getInternalVersionHistory().getVersionLabels();
            String[] ret = new String[labels.length];
            for (int i = 0; i < labels.length; i++) {
                ret[i] = NameFormat.format(labels[i], session.getNamespaceResolver());
            }
            return ret;
        } catch (NoPrefixDeclaredException e) {
            throw new IllegalArgumentException("Unable to resolve label name: " + e.toString());
        }
    }

    /**
     * @see javax.jcr.version.VersionHistory#getVersionLabels(javax.jcr.version.Version)
     */
    public String[] getVersionLabels(Version version)
            throws VersionException, RepositoryException {
        checkOwnVersion(version);
        try {
            QName[] labels = ((VersionImpl) version).getInternalVersion().getLabels();
            String[] ret = new String[labels.length];
            for (int i = 0; i < labels.length; i++) {
                ret[i] = NameFormat.format(labels[i], session.getNamespaceResolver());
            }
            return ret;
        } catch (NoPrefixDeclaredException e) {
            throw new IllegalArgumentException("Unable to resolve label name: " + e.toString());
        }
    }

    /**
     * @see javax.jcr.version.VersionHistory#hasVersionLabel(String)
     */
    public boolean hasVersionLabel(String label) throws RepositoryException {
        try {
            QName qLabel = NameFormat.parse(label, session.getNamespaceResolver());
            return getInternalVersionHistory().getVersionByLabel(qLabel) != null;
        } catch (IllegalNameException e) {
            throw new IllegalArgumentException("Unable to resolve label: " + e);
        } catch (UnknownPrefixException e) {
            throw new IllegalArgumentException("Unable to resolve label: " + e);
        }
    }

    /**
     * @see javax.jcr.version.VersionHistory#hasVersionLabel(javax.jcr.version.Version, String)
     */
    public boolean hasVersionLabel(Version version, String label)
            throws VersionException, RepositoryException {
        checkOwnVersion(version);
        try {
            QName qLabel = NameFormat.parse(label, session.getNamespaceResolver());
            return ((VersionImpl) version).getInternalVersion().hasLabel(qLabel);
        } catch (IllegalNameException e) {
            throw new VersionException(e);
        } catch (UnknownPrefixException e) {
            throw new VersionException(e);
        }
    }

    /**
     * @see javax.jcr.version.VersionHistory#removeVersion(String)
     */
    public void removeVersion(String versionName)
            throws UnsupportedRepositoryOperationException, VersionException,
            RepositoryException {
        try {
            session.getVersionManager().removeVersion(this,
                    NameFormat.parse(versionName, session.getNamespaceResolver()));
        } catch (IllegalNameException e) {
            throw new RepositoryException(e);
        } catch (UnknownPrefixException e) {
            throw new RepositoryException(e);
        }
    }

    /**
     * @see javax.jcr.Item#isSame(javax.jcr.Item)
     */
    public boolean isSame(Item otherItem) {
        if (otherItem instanceof VersionHistoryImpl) {
            // since all version histories live in the same workspace, we can compare the uuids
            try {
                InternalVersionHistory other = ((VersionHistoryImpl) otherItem).getInternalVersionHistory();
                return other.getId().equals(getInternalVersionHistory().getId());
            } catch (RepositoryException e) {
                log.warn("Unable to retrieve internal version history objects: " + e.getMessage());
                log.debug("Stack dump:", e);
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public String getVersionableUUID() throws RepositoryException {
        return getInternalVersionHistory().getVersionableUUID().toString();
    }

    /**
     * Checks if the given version belongs to this history
     *
     * @param version
     * @throws javax.jcr.version.VersionException
     * @throws javax.jcr.RepositoryException
     */
    private void checkOwnVersion(Version version)
            throws VersionException, RepositoryException {
        if (!version.getParent().isSame(this)) {
            throw new VersionException("Specified version not contained in this history.");
        }
    }

    //--------------------------------------< Overwrite "protected" methods >---

    /**
     * Always throws a {@link javax.jcr.nodetype.ConstraintViolationException} since this node
     * is protected.
     *
     * @throws javax.jcr.nodetype.ConstraintViolationException
     */
    public void update(String srcWorkspaceName) throws ConstraintViolationException {
        String msg = "update operation not allowed on a version history node: " + safeGetJCRPath();
        log.debug(msg);
        throw new ConstraintViolationException(msg);
    }

    /**
     * Always throws a {@link javax.jcr.nodetype.ConstraintViolationException} since this node
     * is protected.
     *
     * @throws javax.jcr.nodetype.ConstraintViolationException
     */
    public NodeIterator merge(String srcWorkspace, boolean bestEffort)
            throws ConstraintViolationException {
        String msg = "merge operation not allowed on a version history node: " + safeGetJCRPath();
        log.debug(msg);
        throw new ConstraintViolationException(msg);
    }
}
