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
package org.apache.jackrabbit.jcr2spi.operation;

import org.apache.jackrabbit.jcr2spi.util.LogUtil;
import org.apache.jackrabbit.jcr2spi.HierarchyManager;
import org.apache.jackrabbit.name.Path;
import org.apache.jackrabbit.name.QName;
import org.apache.jackrabbit.name.MalformedPathException;
import org.apache.jackrabbit.name.NamespaceResolver;
import org.apache.jackrabbit.spi.NodeId;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import javax.jcr.RepositoryException;
import javax.jcr.AccessDeniedException;
import javax.jcr.ItemExistsException;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.version.VersionException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.lock.LockException;

/**
 * <code>Move</code>...
 */
public class Move extends AbstractOperation {

    private static Logger log = LoggerFactory.getLogger(Move.class);

    private final NodeId srcId;
    private final NodeId srcParentId;
    private final NodeId destParentId;
    private final QName destName;

    private Move(NodeId srcNodeId, NodeId srcParentId, NodeId destParentId, QName destName) {
        srcId = srcNodeId;
        this.srcParentId = srcParentId;
        this.destParentId = destParentId;
        this.destName = destName;
        addAffectedItemId(srcNodeId);
        addAffectedItemId(srcParentId);
        addAffectedItemId(destParentId);
    }

    //----------------------------------------------------------< Operation >---
    /**
     *
     * @param visitor
     */
    public void accept(OperationVisitor visitor) throws LockException, ConstraintViolationException, AccessDeniedException, ItemExistsException, UnsupportedRepositoryOperationException, VersionException, RepositoryException {
        visitor.visit(this);
    }

    //----------------------------------------< Access Operation Parameters >---
    public NodeId getNodeId() {
        return srcId;
    }

    public NodeId getSourceParentId() {
        return srcParentId;
    }

    public NodeId getDestinationParentId() {
        return destParentId;
    }

    public QName getDestinationName() {
        return destName;
    }

    //------------------------------------------------------------< Factory >---
    public static Operation create(Path srcPath, Path destPath, HierarchyManager hierMgr, NamespaceResolver nsResolver) throws RepositoryException , NoSuchNodeTypeException {
        // src must not be ancestor of destination
        try {
            if (srcPath.isAncestorOf(destPath)) {
                String msg = "Invalid destination path: cannot be descendant of source path (" + LogUtil.safeGetJCRPath(destPath, nsResolver) + "," + LogUtil.safeGetJCRPath(srcPath, nsResolver) + ")";
                log.debug(msg);
                throw new RepositoryException(msg);
            }
        } catch (MalformedPathException e) {
            String msg = "Invalid destination path: cannot be descendant of source path (" +LogUtil.safeGetJCRPath(destPath, nsResolver) + "," + LogUtil.safeGetJCRPath(srcPath, nsResolver) + ")";
            log.debug(msg);
            throw new RepositoryException(msg, e);
        }
        Path.PathElement destElement = destPath.getNameElement();
        // destination must not contain an index
        int index = destElement.getIndex();
        if (index > org.apache.jackrabbit.name.Path.INDEX_UNDEFINED) {
            // subscript in name element
            String msg = "Invalid destination path: subscript in name element is not allowed (" + LogUtil.safeGetJCRPath(destPath, nsResolver) + ")";
            log.debug(msg);
            throw new RepositoryException(msg);
        }
        // root node cannot be moved:
        if (Path.ROOT.equals(srcPath) || Path.ROOT.equals(destPath)) {
            String msg = "Cannot move the root node.";
            log.debug(msg);
            throw new RepositoryException(msg);
        }

        NodeId srcId = getNodeId(srcPath, hierMgr, nsResolver);
        NodeId srcParentId = getNodeId(srcPath.getAncestor(1), hierMgr, nsResolver);
        NodeId destParentId = getNodeId(destPath.getAncestor(1), hierMgr, nsResolver);
        Move move = new Move(srcId, srcParentId, destParentId, destElement.getName());
        return move;
    }
}