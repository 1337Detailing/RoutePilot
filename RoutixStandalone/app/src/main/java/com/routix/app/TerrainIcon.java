package com.routix.app;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Small shared stroke icon family, independent of the device emoji font. */
final class TerrainIcon extends Drawable {
    private final String kind;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    TerrainIcon(String kind,int color){this.kind=kind;paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.8f);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStrokeCap(Paint.Cap.ROUND);}
    @Override public void draw(Canvas canvas){canvas.save();canvas.translate(getBounds().left,getBounds().top);canvas.scale(getBounds().width()/24f,getBounds().height()/24f);
        switch(kind){
            case "map":Path path=new Path();path.moveTo(3,5);path.lineTo(9,3);path.lineTo(15,5);path.lineTo(21,3);path.lineTo(21,19);path.lineTo(15,21);path.lineTo(9,19);path.lineTo(3,21);path.close();canvas.drawPath(path,paint);canvas.drawLine(9,3,9,19,paint);canvas.drawLine(15,5,15,21,paint);break;
            case "record":canvas.drawCircle(12,12,9,paint);canvas.drawCircle(12,12,4,paint);break;
            case "routes":for(int y=6;y<=18;y+=6){canvas.drawCircle(4,y,1,paint);canvas.drawLine(9,y,21,y,paint);}break;
            default:for(int x=5;x<=19;x+=7)canvas.drawCircle(x,12,1.5f,paint);
        }canvas.restore();}
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}@Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
