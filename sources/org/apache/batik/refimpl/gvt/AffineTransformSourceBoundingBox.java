/*****************************************************************************
 * Copyright (C) The Apache Software Foundation. All rights reserved.        *
 * ------------------------------------------------------------------------- *
 * This software is published under the terms of the Apache Software License *
 * version 1.1, a copy of which has been included with this distribution in  *
 * the LICENSE file.                                                         *
 *****************************************************************************/

package org.apache.batik.refimpl.gvt;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.util.awt.geom.AffineTransformSource;

/**
 * Computes the <tt>AffineTransform</tt> based on the referenced 
 * <tt>GraphicsNode</tt>'s bounding box.
 *
 * @author <a href="mailto:vincent.hardy@eng.sun.com">Vincent Hardy</a>
 * @version $Id$
 */

public class AffineTransformSourceBoundingBox implements AffineTransformSource {

    /**
     * <tt>GraphicsNode</tt> whose bounding box defines the
     * point space
     */
    private GraphicsNode node;

    /**
     * @param node <tt>GraphicsNode</tt> used to compute bounding
     *        box space to user space transform.
     */
    public AffineTransformSourceBoundingBox(GraphicsNode node){
        if(node == null){
            throw new IllegalArgumentException();
        }

        this.node = node;
    }

    /**
     * @returns AffineTransform generated by this source
     */
    public AffineTransform getTransform(){
        Rectangle2D bounds = node.getPrimitiveBounds();
        if(bounds == null){
            throw new Error();
        }

        AffineTransform txf = new AffineTransform();
        txf.translate(bounds.getX(),
                      bounds.getY());

        txf.scale(bounds.getWidth(),
                  bounds.getHeight());
        
        return txf;
    }

    public Object clone(){
        return new AffineTransformSourceBoundingBox(node);
    }
}

