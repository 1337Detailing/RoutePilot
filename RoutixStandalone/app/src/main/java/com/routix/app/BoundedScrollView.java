package com.routix.app;

import android.content.Context;
import android.view.View;
import android.widget.ScrollView;

/** Keeps the recording controls above the dock even on short screens/large fonts. */
final class BoundedScrollView extends ScrollView {
    private int maxHeight=Integer.MAX_VALUE;
    BoundedScrollView(Context c){super(c);setFillViewport(false);setClipToOutline(true);}
    void setMaximumHeight(int height){height=Math.max(1,height);if(maxHeight!=height){maxHeight=height;requestLayout();}}
    @Override protected void onMeasure(int width,int height){
        int cap=View.MeasureSpec.getMode(height)==View.MeasureSpec.UNSPECIFIED?maxHeight:Math.min(maxHeight,View.MeasureSpec.getSize(height));
        super.onMeasure(width,View.MeasureSpec.makeMeasureSpec(cap,View.MeasureSpec.AT_MOST));
    }
}
