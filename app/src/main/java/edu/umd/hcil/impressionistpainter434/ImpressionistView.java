package edu.umd.hcil.impressionistpainter434;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.provider.MediaStore;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import java.text.MessageFormat;
import java.util.Random;

/**
 * Created by jon on 3/20/2016.
 */
public class ImpressionistView extends View {

    private ImageView _imageView;

    private Canvas _offScreenCanvas = null;
    private Bitmap _offScreenBitmap = null;
    private Paint _paint = new Paint();
    private Paint _imagePaint = new Paint();

    private float oldX, oldY, startTime;
    private int _alpha = 150;
    private int _defaultRadius = 25;
    private Point _lastPoint = null;
    private long _lastPointTime = -1;
    private boolean _useMotionSpeedForBrushStrokeSize = true;
    private Paint _paintBorder = new Paint();
    private BrushType _brushType = BrushType.Square;
    private float _minBrushRadius = 5;

    public ImpressionistView(Context context) {
        super(context);
        init(null, 0);
    }

    public ImpressionistView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public ImpressionistView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    /**
     * Because we have more than one constructor (i.e., overloaded constructors), we use
     * a separate initialization method
     * @param attrs
     * @param defStyle
     */
    private void init(AttributeSet attrs, int defStyle){

        // Set setDrawingCacheEnabled to true to support generating a bitmap copy of the view (for saving)
        // See: http://developer.android.com/reference/android/view/View.html#setDrawingCacheEnabled(boolean)
        //      http://developer.android.com/reference/android/view/View.html#getDrawingCache()
        this.setDrawingCacheEnabled(true);

        _paint.setColor(Color.RED);
        _paint.setAlpha(_alpha);
        _paint.setAntiAlias(true);
        _paint.setStyle(Paint.Style.FILL);
        _paint.setStrokeWidth(4);

        _paintBorder.setColor(Color.BLACK);
        _paintBorder.setStrokeWidth(3);
        _paintBorder.setStyle(Paint.Style.STROKE);
        _paintBorder.setAlpha(50);

        //_paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
    }

    public void saveImpressionist(Context context) {
        MediaStore.Images.Media.insertImage(context.getContentResolver(), _offScreenBitmap, "Title", "Description");
    }
    @Override
    protected void onSizeChanged (int w, int h, int oldw, int oldh){

        Bitmap bitmap = getDrawingCache();
        Log.v("onSizeChanged", MessageFormat.format("bitmap={0}, w={1}, h={2}, oldw={3}, oldh={4}", bitmap, w, h, oldw, oldh));
        if(bitmap != null) {
            _offScreenBitmap = getDrawingCache().copy(Bitmap.Config.ARGB_8888, true);
            _offScreenCanvas = new Canvas(_offScreenBitmap);
        }
    }

    /**
     * Sets the ImageView, which hosts the image that we will paint in this view
     * @param imageView
     */
    public void setImageView(ImageView imageView){
        _imageView = imageView;
    }

    /**
     * Sets the brush type. Feel free to make your own and completely change my BrushType enum
     * @param brushType
     */
    public void setBrushType(BrushType brushType){
        _brushType = brushType;
    }

    /**
     * Clears the painting
     */
    public void clearPainting(){
        if(_offScreenCanvas != null) {
            Paint paint = new Paint();
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.FILL);
            _offScreenCanvas.drawRect(0, 0, this.getWidth(), this.getHeight(), paint);
        }
        invalidate();
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if(_offScreenBitmap != null) {
            canvas.drawBitmap(_offScreenBitmap, 0, 0, _paint);
        }

        // Draw the border. Helpful to see the size of the bitmap in the ImageView
        canvas.drawRect(getBitmapPositionInsideImageView(_imageView), _paintBorder);
    }

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent){
        float curTouchX = motionEvent.getX();
        float curTouchY = motionEvent.getY();
        switch(motionEvent.getAction()){
            case MotionEvent.ACTION_DOWN:
                // Track old coordinates and start timer
                oldX = motionEvent.getX();
                oldY = motionEvent.getY();
                startTime = motionEvent.getEventTime();
            case MotionEvent.ACTION_MOVE:
                // Compute distance and velocity of finger swipe
                double distance = Math.sqrt(((curTouchX-oldX) * (curTouchX-oldX)) + ((curTouchY-oldY) * (curTouchY-oldY)));
                float endTime = motionEvent.getEventTime();

                // Velocity usually doesn't exceed 6, so normalize brush radius between 5 and 95 (subject to change)
                float radius = calculateBrushSizeFromVelocity(distance, endTime, startTime);
                int historySize = motionEvent.getHistorySize();

                for (int i = 0; i < historySize; i++) {

                    float touchX = motionEvent.getHistoricalX(i);
                    float touchY = motionEvent.getHistoricalY(i);

                    // Get color from point in image
                    Bitmap imageViewBitmap = _imageView.getDrawingCache();
                    int pixelColor = imageViewBitmap.getPixel((int) touchX, (int) touchY);
                    _paint.setColor(pixelColor);
                    _paint.setAlpha(_alpha);

                    if (_brushType == BrushType.Square) {
                        _offScreenCanvas.drawRect(touchX, touchY, touchX + radius, touchY + radius, _paint);
                    }
                    else if(_brushType == BrushType.Circle) {
                        _offScreenCanvas.drawCircle(touchX, touchY, radius, _paint);
                    }
                    else if(_brushType == BrushType.CircleSplatter) {
                        // draw 5 circles randomly distributed around point
                        for (int j = 0; j < 5; j++) {
                            // generate pos/neg X and Y factors
                            float factorX = (float) Math.random();
                            factorX *= (Math.random() > .5) ? 1 : -1;
                            float factorY = (float) Math.random();
                            factorY *= (Math.random() > .5) ? 1 : -1;

                            // shift by portion of radius
                            _offScreenCanvas.drawCircle(curTouchX + (factorX * radius), curTouchY + (factorY * radius), radius, _paint);
                        }
                    } else if(_brushType == BrushType.BlackWhite) {
                        int red = Color.red(pixelColor);
                        int green = Color.green(pixelColor);
                        int blue = Color.blue(pixelColor);
                        int absColor = (int)(red*0.2126) + (int)(green*0.7152) + (int)(blue*0.0722);
                        _paint.setARGB(_alpha,absColor,absColor,absColor);
                        _offScreenCanvas.drawCircle(curTouchX, curTouchY, 50, _paint);
                    }
                    else {
                        _offScreenCanvas.drawPoint(touchX, touchY, _paint);
                    }
                }
                Bitmap imageViewBitmap = _imageView.getDrawingCache();
                int pixelColor = imageViewBitmap.getPixel((int)curTouchX, (int)curTouchY);
                _paint.setColor(pixelColor);
                _paint.setAlpha(_alpha);

                if (_brushType == BrushType.Square) {
                    _offScreenCanvas.drawRect(curTouchX, curTouchY, curTouchX + radius, curTouchY + radius, _paint);
                }
                else if(_brushType == BrushType.Circle) {
                    _offScreenCanvas.drawCircle(curTouchX, curTouchY, radius, _paint);
                }
                else if(_brushType == BrushType.CircleSplatter){
                    for (int j = 0; j < 5; j++) {
                        float factorX = (float) Math.random();
                        factorX *= (Math.random() > .5) ? 1 : -1;
                        float factorY = (float) Math.random();
                        factorY *= (Math.random() > .5) ? 1 : -1;
                        _offScreenCanvas.drawCircle(curTouchX + (factorX * radius), curTouchY + (factorY * radius), radius, _paint);
                    }
                }
                else if(_brushType == BrushType.BlackWhite) {
                    int red = Color.red(pixelColor);
                    int green = Color.green(pixelColor);
                    int blue = Color.blue(pixelColor);
                    int absColor = (int)(red*0.2126) + (int)(green*0.7152) + (int)(blue*0.0722);
                    _paint.setARGB(_alpha,absColor,absColor,absColor);
                    _offScreenCanvas.drawCircle(curTouchX, curTouchY, 20, _paint);
                }
                else {
                    _offScreenCanvas.drawPoint(curTouchX, curTouchY, _paint);
                }
                oldX = curTouchX;
                oldY = curTouchY;
                startTime = motionEvent.getEventTime();
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                break;
        }

        return true;
    }

    private float calculateBrushSizeFromVelocity(double distance, float endTime, float startTime) {
        float velocity =(float) distance / (endTime - startTime);
        float radius = (18 * velocity) + _minBrushRadius;
        return radius;
    }

    /**
     * This method is useful to determine the bitmap position within the Image View. It's not needed for anything else
     * Modified from:
     *  - http://stackoverflow.com/a/15538856
     *  - http://stackoverflow.com/a/26930938
     * @param imageView
     * @return
     */
    private static Rect getBitmapPositionInsideImageView(ImageView imageView){
        Rect rect = new Rect();

        if (imageView == null || imageView.getDrawable() == null) {
            return rect;
        }

        // Get image dimensions
        // Get image matrix values and place them in an array
        float[] f = new float[9];
        imageView.getImageMatrix().getValues(f);

        // Extract the scale values using the constants (if aspect ratio maintained, scaleX == scaleY)
        final float scaleX = f[Matrix.MSCALE_X];
        final float scaleY = f[Matrix.MSCALE_Y];

        // Get the drawable (could also get the bitmap behind the drawable and getWidth/getHeight)
        final Drawable d = imageView.getDrawable();
        final int origW = d.getIntrinsicWidth();
        final int origH = d.getIntrinsicHeight();

        // Calculate the actual dimensions
        final int widthActual = Math.round(origW * scaleX);
        final int heightActual = Math.round(origH * scaleY);

        // Get image position
        // We assume that the image is centered into ImageView
        int imgViewW = imageView.getWidth();
        int imgViewH = imageView.getHeight();

        int top = (int) (imgViewH - heightActual)/2;
        int left = (int) (imgViewW - widthActual)/2;

        rect.set(left, top, left + widthActual, top + heightActual);

        return rect;
    }
}

