/*
 * Project:  NextGIS Mobile
 * Purpose:  Mobile GIS for Android.
 * Author:   Dmitry Baryshnikov (aka Bishop), bishop.dev@gmail.com
 * *****************************************************************************
 * Copyright (c) 2012-2015. NextGIS, info@nextgis.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextgis.maplibui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.Scroller;
import com.nextgis.maplib.api.ILayer;
import com.nextgis.maplib.api.ILayerView;
import com.nextgis.maplib.api.MapEventListener;
import com.nextgis.maplib.datasource.GeoEnvelope;
import com.nextgis.maplib.datasource.GeoPoint;
import com.nextgis.maplib.map.LayerGroup;
import com.nextgis.maplib.map.MapDrawable;
import com.nextgis.maplibui.api.MapViewEventListener;

import static com.nextgis.maplibui.util.ConstantsUI.*;
import static com.nextgis.maplib.util.Constants.*;

public class MapView
        extends MapViewBase
        implements GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener,  ScaleGestureDetector.OnScaleGestureListener,
                   MapEventListener
{
    protected final GestureDetector      mGestureDetector;
    protected final ScaleGestureDetector mScaleGestureDetector;
    protected       PointF               mStartMouseLocation;
    protected       PointF               mCurrentMouseOffset;
    protected       PointF               mCurrentFocusLocation;
    protected       int                  mDrawingState;
    protected       double               mScaleFactor;
    protected       double               mCurrentSpan;
    protected       Scroller             mScroller;
    protected       long                 mStartDrawTime;
    protected       long                 mTopVisibleLayerId;

    //display redraw timeout ms
    public static final int DISPLAY_REDRAW_TIMEOUT = 1650;
    protected final Handler mHandler = new Handler(Looper.getMainLooper());


    public MapView(
            Context context,
            MapDrawable map)
    {
        super(context, map);

        mGestureDetector = new GestureDetector(context, this);
        mGestureDetector.setOnDoubleTapListener(this);

        mScaleGestureDetector = new ScaleGestureDetector(getContext(), this);

        mScroller = new Scroller(context);

        mStartMouseLocation = new PointF();
        mCurrentMouseOffset = new PointF();
        mCurrentFocusLocation = new PointF();

        mDrawingState = DRAW_SATE_drawing_noclearbk;

        findTopVisibleLayer(mMap);
    }


    protected boolean findTopVisibleLayer(LayerGroup group)
    {
        if(null == group)
            return false;

        for (int i = group.getLayerCount() - 1; i >= 0; i--) {
            ILayer layer = group.getLayer(i);
            if (layer instanceof LayerGroup) {
                if (findTopVisibleLayer((LayerGroup) layer))
                    return true;
            }
            ILayerView view = (ILayerView) group.getLayer(i);
            if (view.isVisible()) {
                mTopVisibleLayerId = layer.getId();
                return true;
            }
        }
        return false;
    }


    @Override
    protected void onAttachedToWindow()
    {
        super.onAttachedToWindow();

        if (mMap != null) {
            mMap.addListener(this);
        }
    }


    @Override
    protected void onDetachedFromWindow()
    {
        super.onDetachedFromWindow();

        if (mMap != null) {
            mMap.removeListener(this);
        }
    }


    @Override
    protected synchronized void onDraw(Canvas canvas)
    {
        // Log.d(TAG, "state: " + mDrawingState + ", current loc: " +  mCurrentMouseOffset.toString() + " current focus: " + mCurrentFocusLocation.toString() + " scale: "  + mScaleFactor);

        if (mMap != null) {

            switch (mDrawingState) {

                case DRAW_SATE_panning:
                case DRAW_SATE_panning_fling:
                    canvas.drawBitmap(
                            mMap.getView(-mCurrentMouseOffset.x, -mCurrentMouseOffset.y, true), 0,
                            0, null);
                    break;

                case DRAW_SATE_zooming:
                    canvas.drawBitmap(
                            mMap.getView(-mCurrentFocusLocation.x, -mCurrentFocusLocation.y,
                                         (float) mScaleFactor), 0, 0, null);
                    break;
//TODO: add invalidate rect to prevent flicker
                case DRAW_SATE_drawing_noclearbk:
                    canvas.drawBitmap(mMap.getView(false), 0, 0, null);
                    break;

                case DRAW_SATE_drawing:
                    canvas.drawBitmap(mMap.getView(true), 0, 0, null);
                    break;

                default: // mDrawingState == DRAW_SATE_none
                    break;
            }

        } else {
            super.onDraw(canvas);
        }
    }

    protected void zoomStart(ScaleGestureDetector scaleGestureDetector){
        mHandler.removeCallbacksAndMessages(null);

        if(mDrawingState == DRAW_SATE_zooming)
            return;

        mDrawingState = DRAW_SATE_zooming;
        mCurrentSpan = scaleGestureDetector.getCurrentSpan();
        mCurrentFocusLocation.set(-scaleGestureDetector.getFocusX(), -scaleGestureDetector.getFocusY());
        mScaleFactor = 1.f;
    }

    protected void zoom(ScaleGestureDetector scaleGestureDetector){
        if(mDrawingState != DRAW_SATE_zooming)
            zoomStart(scaleGestureDetector);


        if(mDrawingState == DRAW_SATE_zooming && mMap != null) {
            double scaleFactor = scaleGestureDetector.getScaleFactor() * scaleGestureDetector.getCurrentSpan() / mCurrentSpan;
            double zoom = getZoomForScaleFactor(scaleFactor);
            if(zoom < mMap.getMinZoom() || zoom > mMap.getMaxZoom())
                return;

            mScaleFactor = scaleFactor;
            invalidate();
        }
    }

    protected float getZoomForScaleFactor(double scale){
        if(mMap == null)
            return 1;
        float zoom = mMap.getZoomLevel();

        if(scale > 1){
            zoom = (float) (mMap.getZoomLevel() + lg(scale));
        }
        else if(scale < 1){
            zoom = (float) (mMap.getZoomLevel() - lg( 1 / scale));
        }
        return zoom;
    }

    protected void zoomStop(){
        if(mDrawingState == DRAW_SATE_zooming && mMap != null) {

            float zoom = getZoomForScaleFactor(mScaleFactor);

            GeoEnvelope env = mMap.getFullBounds();
            GeoPoint focusPt = new GeoPoint(-mCurrentFocusLocation.x, -mCurrentFocusLocation.y);

            double invertScale = 1 / mScaleFactor;

            double offX = (1 - invertScale) * focusPt.getX();
            double offY = (1 - invertScale) * focusPt.getY();
            env.scale(invertScale);
            env.offset(offX, offY);

            GeoPoint newCenterPt = env.getCenter();
            GeoPoint newCenterPtMap = mMap.screenToMap(newCenterPt);

            Log.d(TAG, "zoomStop: setZoomAndCenter");
            setZoomAndCenter(zoom, newCenterPtMap);

            asyncSetDrawingState(true);
        }
    }

    protected void panStart(final MotionEvent e){
        mHandler.removeCallbacksAndMessages(null);

        if (mDrawingState == DRAW_SATE_zooming
            || mDrawingState == DRAW_SATE_panning
            || mDrawingState == DRAW_SATE_panning_fling)
            return;

        Log.d(TAG, "panStart");
        for (MapViewEventListener listener : mListeners){
            if(null != listener)
                listener.panStart(e);
        }

        mDrawingState = DRAW_SATE_panning;
        mStartMouseLocation.set(e.getX(), e.getY());
        mCurrentMouseOffset.set(0, 0);
    }

    protected void panMoveTo(final MotionEvent e){
        if(mDrawingState == DRAW_SATE_zooming || mDrawingState == DRAW_SATE_drawing_noclearbk)
            return;

        if(mDrawingState == DRAW_SATE_panning && mMap != null){
            for (MapViewEventListener listener : mListeners){
                if(null != listener)
                    listener.panMoveTo(e);
            }

            float x = mStartMouseLocation.x - e.getX();
            float y = mStartMouseLocation.y - e.getY();

            //Log.d(TAG, "panMoveTo x - " + x + " y - " + y);

            GeoEnvelope bounds = mMap.getFullBounds();
            bounds.offset(x, y);

            GeoEnvelope limits = mMap.getLimits();

            if (bounds.getMinY() <= limits.getMinY() || bounds.getMaxY() >= limits.getMaxY()) {
                y = mCurrentMouseOffset.y;
            }

            if (bounds.getMinX() <= limits.getMinX() || bounds.getMaxX() >= limits.getMaxX()) {
                x = mCurrentMouseOffset.x;
            }

            mCurrentMouseOffset.set(x, y);
            invalidate();
        }
    }

    protected void panStop(){
        //Log.d(Constants.TAG, "panStop state: " + mDrawingState);

        if(mDrawingState == DRAW_SATE_panning && mMap != null) {

            float x = mCurrentMouseOffset.x;
            float y = mCurrentMouseOffset.y;

            //Log.d(TAG, "panStop x - " + x + " y - " + y);

            GeoEnvelope bounds = mMap.getFullBounds();
            bounds.offset(x, y);
            GeoEnvelope mapBounds = mMap.screenToMap(bounds);

            GeoPoint pt = mapBounds.getCenter();
            //GeoPoint screenPt = mDisplay.mapToScreen(new GeoPoint(mapBounds.getMinX(), mapBounds.getMinY()));
            //Log.d(TAG, "panStop. x: " + x + ", y:" + y + ", sx:" + screenPt.getX() + ", sy:" + screenPt.getY());
            //mDisplay.panStop((float) screenPt.getX(), (float) screenPt.getY());

            Log.d(TAG, "panStop: setZoomAndCenter");
            setZoomAndCenter(getZoomLevel(), pt);

            for (MapViewEventListener listener : mListeners){
                if(null != listener)
                    listener.panStop();
            }

            asyncSetDrawingState(true);
        }
    }


    // delegate the event to the gesture detector
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mScaleGestureDetector.onTouchEvent(event);

        if (!mGestureDetector.onTouchEvent(event)) {
            switch (event.getAction()) { //TODO: get action can be more complicated: if ((event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_DOWN)
                case MotionEvent.ACTION_DOWN:
                    if (!mScroller.isFinished()){
                        mScroller.forceFinished(true);
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                    break;

                case MotionEvent.ACTION_UP:
                    panStop();
                    break;

                default:
                    break;
            }
        }

        return true;
    }

    @Override
    public boolean onDown(MotionEvent e) {
        //Log.d(TAG, "onDown: " + e.toString());
        return false;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if(mMap == null) //fling not always exec panStop
            return false;
        float x = mCurrentMouseOffset.x;
        float y = mCurrentMouseOffset.y;
        GeoEnvelope bounds = mMap.getLimits();
        mDrawingState = DRAW_SATE_panning_fling;

        mScroller.forceFinished(true);

        mScroller.fling((int)x, (int)y, -(int)velocityX, -(int)velocityY, (int)bounds.getMinX(), (int)bounds.getMaxX(), (int)bounds.getMinY(), (int)bounds.getMaxY());

        //Log.d(Constants.TAG, "Fling");

        postInvalidate();

        return true;
    }

    @Override
    public void computeScroll()
    {
        super.computeScroll();
        if(mDrawingState == DRAW_SATE_panning_fling && mMap != null) {
            if (mScroller.computeScrollOffset()) {
                if (mScroller.isFinished()) {
                    mDrawingState = DRAW_SATE_panning;
                    panStop();
                } else {
                    float x = mScroller.getCurrX();
                    float y = mScroller.getCurrY();

                    GeoEnvelope bounds = mMap.getFullBounds();
                    bounds.offset(x, y);

                    GeoEnvelope limits = mMap.getLimits();

                    if (bounds.getMinY() <= limits.getMinY() || bounds.getMaxY() >= limits.getMaxY()) {
                        y = mCurrentMouseOffset.y;
                    }

                    if (bounds.getMinX() <= limits.getMinX() || bounds.getMaxX() >= limits.getMaxX()) {
                        x = mCurrentMouseOffset.x;
                    }
                    findTopVisibleLayer(mMap);

                    mCurrentMouseOffset.set(x, y);

                    postInvalidate();
                }
            } else if (mScroller.isFinished()) {
                mDrawingState = DRAW_SATE_panning;
                panStop();
            }
        }
    }

    @Override
    public void onLongPress(MotionEvent event) {
        for (MapViewEventListener listener : mListeners){
            if(null != listener)
                listener.onLongPress(event);
        }
    }

    @Override
    public boolean onScroll(MotionEvent event1, MotionEvent event2, float distanceX, float distanceY) {
        if(event2.getPointerCount() > 1)
            return false;
        //Log.d(TAG, "onScroll: " + event1.toString() + ", " + event2.toString() + ", "
        //           + distanceX + ", " + distanceY);

        panStart(event1);
        panMoveTo(event2);
        return true;
    }

    @Override
    public void onShowPress(MotionEvent e) {
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        //Log.d(Constants.TAG, "onSingleTapUp: " + e.toString());
        for (MapViewEventListener listener : mListeners){
            if(null != listener)
                listener.onSingleTapUp(e);
        }
        return false;
    }

    @Override
    public boolean onDoubleTap(final MotionEvent e) {
        if(mMap == null)
            return false;

        mHandler.removeCallbacksAndMessages(null);

        mDrawingState = DRAW_SATE_zooming;
        mScaleFactor = 2;
        mCurrentFocusLocation.set(-e.getX(), -e.getY());
        invalidate();

        GeoEnvelope env = mMap.getFullBounds();
        GeoPoint focusPt = new GeoPoint(-mCurrentFocusLocation.x, -mCurrentFocusLocation.y);

        double invertScale = 1 / mScaleFactor;

        double offX = (1 - invertScale) * focusPt.getX();
        double offY = (1 - invertScale) * focusPt.getY();
        env.scale(invertScale);
        env.offset(offX, offY);

        GeoPoint newCenterPt = env.getCenter();
        GeoPoint newCenterPtMap = mMap.screenToMap(newCenterPt);

        Log.d(TAG, "onDoubleTap: setZoomAndCenter");
        setZoomAndCenter((float)Math.ceil(getZoomLevel() + 0.5), newCenterPtMap);

        asyncSetDrawingState(true);

        return true;
    }

    @Override
    public boolean onDoubleTapEvent(final MotionEvent e) {
        return false;
    }

    @Override
    public boolean onSingleTapConfirmed(final MotionEvent e) {
        return false;
    }


    @Override
    public void zoomIn() {

        mHandler.removeCallbacksAndMessages(null);

        mDrawingState = DRAW_SATE_zooming;
        mScaleFactor = 2;
        mCurrentFocusLocation.set(-getWidth() / 2, -getHeight() / 2);
        invalidate();

        super.zoomIn();

        asyncSetDrawingState(true);
    }

    @Override
    public void zoomOut() {

        mHandler.removeCallbacksAndMessages(null);

        mDrawingState = DRAW_SATE_zooming;
        mScaleFactor = 0.5;
        mCurrentFocusLocation.set(-getWidth() / 2, -getHeight() / 2);
        invalidate();

        super.zoomOut();

        asyncSetDrawingState(true);
    }

    @Override
    public boolean onScale(ScaleGestureDetector scaleGestureDetector) {
        //Log.d(TAG, "onScale");
        zoom(scaleGestureDetector);
        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector scaleGestureDetector) {
        zoomStart(scaleGestureDetector);
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector scaleGestureDetector) {
        zoomStop();
    }

    public static double lg(double x) {
        return Math.log(x)/Math.log(2.0);
    }


    @Override
    public void onLayerAdded(int id)
    {
        findTopVisibleLayer(mMap);
        drawMapDrawable();
    }


    @Override
    public void onLayerDeleted(int id)
    {
        findTopVisibleLayer(mMap);
        drawMapDrawable();
    }


    @Override
    public void onLayerChanged(int id)
    {
        findTopVisibleLayer(mMap);
        drawMapDrawable();
    }


    @Override
    public void onExtentChanged(
            float zoom,
            GeoPoint center)
    {
        drawMapDrawable();
    }


    @Override
    public void onLayersReordered()
    {
        findTopVisibleLayer(mMap);
        drawMapDrawable();
    }

    protected void drawMapDrawable(){
        if(mMap != null) {
            mDrawingState = DRAW_SATE_drawing;
            mStartDrawTime = System.currentTimeMillis();
            mMap.runDraw(null);
        }
    }


    @Override
    public void onLayerDrawFinished(
            int id,
            float percent)
    {
        if(!(mDrawingState == DRAW_SATE_drawing_noclearbk || mDrawingState == DRAW_SATE_drawing))
            return;
        //Log.d(TAG, "onLayerDrawFinished: " + id + " percent " + percent);
        //mDrawingState = DRAW_SATE_drawing_noclearbk;
        if(percent >= 1.0 && id == mTopVisibleLayerId){
            //Log.d(TAG, "LayerDrawFinished: id - " + id + ", percent - " + percent);
            //ILayer layer = mMap.getLastLayer();
            //if(null != layer && layer.getId() == id)
            invalidate();
        }
        else if(System.currentTimeMillis() - mStartDrawTime > DISPLAY_REDRAW_TIMEOUT){
            mStartDrawTime = System.currentTimeMillis();
            postInvalidate();
        }
    }


    public void panTo(GeoPoint center)
    {
        Log.d(TAG, "panTo: setZoomAndCenter");
        setZoomAndCenter(getZoomLevel(), center);
    }

    protected void asyncSetDrawingState(final boolean isPostInvalidate){
        final Runnable s = new Runnable() {
            public void run() {
                //do your stuff here after DELAY sec
                mDrawingState = DRAW_SATE_drawing;
                mStartDrawTime = System.currentTimeMillis();
            }
        };
        mHandler.postDelayed(s, 650);

        final Runnable r = new Runnable() {
            public void run() {
                if(isPostInvalidate)
                    postInvalidate();
                else
                    invalidate();
            }
        };
        mHandler.postDelayed(r, DISPLAY_REDRAW_TIMEOUT);
    }
}
