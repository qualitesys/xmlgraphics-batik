/*

   Copyright 2001-2003  The Apache Software Foundation 

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */
package org.apache.batik.gvt.renderer;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.util.Iterator;
import java.util.Collection;

import org.apache.batik.ext.awt.geom.RectListManager;
import org.apache.batik.ext.awt.image.GraphicsUtil;
import org.apache.batik.ext.awt.image.PadMode;
import org.apache.batik.ext.awt.image.rendered.CachableRed;
import org.apache.batik.ext.awt.image.rendered.PadRed;
import org.apache.batik.util.HaltingThread;

/**
 * Simple implementation of the Renderer that supports dynamic updates.
 *
 * @author <a href="mailto:Thierry.Kormann@sophia.inria.fr">Thierry Kormann</a>
 * @version $Id$
 */
public class DynamicRenderer extends StaticRenderer {

    final static int COPY_OVERHEAD      = 1000;
    final static int COPY_LINE_OVERHEAD = 10;

    /**
     * Constructs a new dynamic renderer with the specified buffer image.
     */
    public DynamicRenderer() {
        super();
    }

    public DynamicRenderer(RenderingHints rh,
                           AffineTransform at){
        super(rh, at);
    }

    RectListManager damagedAreas;

    protected CachableRed setupCache(CachableRed img) {
        // Don't do any caching of content for dynamic case
        return img;
    }

    public void flush(Rectangle r) {
        // Since we don't cache we don't need to flush
        return;
    }

    /**
     * Flush a list of rectangles of cached image data.
     */
    public void flush(Collection areas) {
        // Since we don't cache we don't need to flush
        return;
    }

    protected void updateWorkingBuffers() {
        if (rootFilter == null) {
            rootFilter = rootGN.getGraphicsNodeRable(true);
            rootCR = null;
        }

        rootCR = renderGNR();
        if (rootCR == null) {
            // No image to display so clear everything out...
            workingRaster = null;
            workingOffScreen = null;
            workingBaseRaster = null;
            
            currentOffScreen = null;
            currentBaseRaster = null;
            currentRaster = null;
            return;
        }

        SampleModel sm = rootCR.getSampleModel();
        int         w  = offScreenWidth;
        int         h  = offScreenHeight;

        if ((workingBaseRaster == null) ||
            (workingBaseRaster.getWidth()  < w) ||
            (workingBaseRaster.getHeight() < h)) {

            sm = sm.createCompatibleSampleModel(w, h);
            
            workingBaseRaster 
                = Raster.createWritableRaster(sm, new Point(0,0));

            workingRaster = workingBaseRaster.createWritableChild
                (0, 0, w, h, 0, 0, null);

            workingOffScreen =  new BufferedImage
                (rootCR.getColorModel(), 
                 workingRaster,
                 rootCR.getColorModel().isAlphaPremultiplied(), null);

        }

        if (!isDoubleBuffered) {
            currentOffScreen  = workingOffScreen;
            currentBaseRaster = workingBaseRaster;
            currentRaster     = workingRaster;
        }
    }

    /**
     * Repaints the associated GVT tree under the list of <tt>areas</tt>.
     * 
     * If double buffered is true and this method completes cleanly it
     * will set the result of the repaint as the image returned by
     * getOffscreen otherwise the old image will still be returned.
     * If double buffered is false it is possible some effects of
     * the failed rendering will be visible in the image returned
     * by getOffscreen.
     *
     * @param areas a List of regions to be repainted, in the current
     * user space coordinate system.  
     */
    // long lastFrame = -1;
    public void repaint(RectListManager devRLM) {
        if (devRLM == null)
            return;

        // long t0 = System.currentTimeMillis();
        // if (lastFrame != -1) {
        //     System.out.println("InterFrame time: " + (t0-lastFrame));
        // }
        // lastFrame = t0;

        CachableRed cr;
        WritableRaster syncRaster;
        WritableRaster copyRaster;

        updateWorkingBuffers();
        if ((rootCR == null)           ||
            (workingBaseRaster == null)) {
            // System.out.println("RootCR: " + rootCR);
            // System.out.println("wrkBaseRaster: " + workingBaseRaster);
            return;
        }
        cr = rootCR;
        syncRaster = workingBaseRaster;
        copyRaster = workingRaster;

        Rectangle srcR = rootCR.getBounds();
        // System.out.println("RootCR: " + srcR);
        Rectangle dstR = workingRaster.getBounds();
        if ((dstR.x < srcR.x) ||
            (dstR.y < srcR.y) ||
            (dstR.x+dstR.width  > srcR.x+srcR.width) ||
            (dstR.y+dstR.height > srcR.y+srcR.height))
            cr = new PadRed(cr, dstR, PadMode.ZERO_PAD, null);

        boolean repaintAll = false;

        Rectangle dr = copyRaster.getBounds();

        // Ensure only one thread works on baseRaster at a time...
        synchronized (syncRaster) {
            // System.out.println("Dynamic:");
            if (repaintAll) {
                // System.out.println("Repainting All");
                cr.copyData(copyRaster);
            } else {
                java.awt.Graphics2D g2d = null;
                if (false) {
                    BufferedImage tmpBI = new BufferedImage
                        (workingOffScreen.getColorModel(),
                         copyRaster.createWritableTranslatedChild(0, 0),
                         workingOffScreen.isAlphaPremultiplied(), null);
                    g2d = GraphicsUtil.createGraphics(tmpBI);
                    g2d.translate(-copyRaster.getMinX(), 
                                  -copyRaster.getMinY());
                }

                
                if ((isDoubleBuffered) &&
                    (currentRaster != null) && 
                    (damagedAreas  != null)) {
                    damagedAreas.subtract(devRLM, COPY_OVERHEAD, 
                                          COPY_LINE_OVERHEAD);
                    damagedAreas.mergeRects(COPY_OVERHEAD, 
                                            COPY_LINE_OVERHEAD); 

                    Iterator iter = damagedAreas.iterator();
                    while (iter.hasNext()) {
                        Rectangle r = (Rectangle)iter.next();
                        if (!dr.intersects(r)) continue;
                        r = dr.intersection(r);
                        // System.err.println("Copy: " + r);
                        Raster src = currentRaster.createWritableChild
                            (r.x, r.y, r.width, r.height, r.x, r.y, null);
                        GraphicsUtil.copyData(src, copyRaster);
                        if (g2d != null) {
                            g2d.setPaint(new java.awt.Color(0,0,255,50));
                            g2d.fill(r);
                            g2d.setPaint(new java.awt.Color(0,0,0,50));
                            g2d.draw(r);
                        }
                    }
                }

                Iterator iter = devRLM.iterator();
                while (iter.hasNext()) {
                    Rectangle r = (Rectangle)iter.next();
                    if (!dr.intersects(r)) continue;
                    r = dr.intersection(r);
                    
                    // System.err.println("Render: " + r);
                    WritableRaster dst = copyRaster.createWritableChild
                        (r.x, r.y, r.width, r.height, r.x, r.y, null);
                    cr.copyData(dst);
                    if (g2d != null) {
                        g2d.setPaint(new java.awt.Color(255,0,0,50));
                        g2d.fill(r);
                        g2d.setPaint(new java.awt.Color(0,0,0,50));
                        g2d.draw(r);
                    }
                }
            }
        }

        if (HaltingThread.hasBeenHalted())
            return;

        // System.out.println("Dmg: "   + damagedAreas);
        // System.out.println("Areas: " + devRects);

        // Swap the buffers if the rendering completed cleanly.
        BufferedImage tmpBI = workingOffScreen;
        
        workingBaseRaster = currentBaseRaster;
        workingRaster     = currentRaster;
        workingOffScreen  = currentOffScreen;
        
        currentRaster     = copyRaster;
        currentBaseRaster = syncRaster;
        currentOffScreen  = tmpBI;
        
        damagedAreas = devRLM;
    }
}
