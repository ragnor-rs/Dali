package io.reist.dali;

import android.graphics.Bitmap;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.view.View;

/**
 * Requests for {@link Dali}.
 *
 * Created by m039 on 12/30/15.
 */
@SuppressWarnings("WeakerAccess")
public class ImageRequest {

    public final Object attachTarget;

    public String url = null;
    public int targetWidth = 0;
    public int targetHeight = 0;
    public ImageRequestTransformer transformer = ImageRequestTransformer.IDENTITY;
    public boolean defer = true;
    public boolean inCircle = false;
    public Bitmap.Config config = Bitmap.Config.ARGB_8888;
    public @DrawableRes int placeholderRes;
    public boolean blur = false;
    public boolean disableTransformation = false;
    public ImageLoader imageLoader = null;
    public ScaleMode scaleMode = ScaleMode.CENTER_INSIDE;

    public ImageRequest() {
        attachTarget = null;
    }

    public ImageRequest(@NonNull Object attachTarget) {
        this.attachTarget = attachTarget;
    }

    public ImageRequest url(String url) {
        this.url = url;
        return this;
    }

    @SuppressWarnings("unused")
    public ImageRequest transformer(ImageRequestTransformer transformer) {
        this.transformer = transformer;
        return this;
    }

    @SuppressWarnings("unused")
    public ImageRequest scaleMode(ScaleMode scaleMode) {
        this.scaleMode = scaleMode;
        return this;
    }

    /**
     * @param defer     image loading will be deferred until an image be measured
     */
    @SuppressWarnings("unused")
    public ImageRequest defer(boolean defer) {
        this.defer = defer;
        return this;
    }

    @SuppressWarnings("unused")
    public ImageRequest inCircle(boolean inCircle) {
        this.inCircle = inCircle;
        return this;
    }

    @SuppressWarnings("unused")
    public ImageRequest config(Bitmap.Config config) {
        this.config = config;
        return this;
    }

    @SuppressWarnings("unused")
    public ImageRequest placeholder(@DrawableRes int placeholderRes) {
        this.placeholderRes = placeholderRes;
        return this;
    }

    @SuppressWarnings("unused")
    public ImageRequest blur(boolean blur) {
        this.blur = blur;
        return this;
    }

    public void into(@NonNull View view) {
        into(view, false);
    }

    public void into(@NonNull View view, boolean background) {
        if (imageLoader != null) {
            imageLoader.load(this, view, background);
        }
    }

    public void into(@NonNull DaliCallback callback) {
        if (imageLoader != null) {
            imageLoader.load(this, callback);
        }
    }

    public ImageRequest targetSize(int w, int h) {
        targetWidth = w;
        targetHeight = h;
        return this;
    }

    @SuppressWarnings("unused")
    public ImageRequest disableTransformation(boolean disableTransformation) {
        this.disableTransformation = disableTransformation;
        return this;
    }

    public ImageRequest imageLoader(ImageLoader imageLoader) {
        this.imageLoader = imageLoader;
        return this;
    }

}