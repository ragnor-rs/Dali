package io.reist.dali.glide;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.view.View;

import com.bumptech.glide.BitmapTypeRequest;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;
import com.bumptech.glide.load.resource.bitmap.TransformationUtils;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.animation.NoAnimation;
import com.bumptech.glide.request.animation.ViewPropertyAnimation;
import com.bumptech.glide.request.target.BaseTarget;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.target.SizeReadyCallback;
import com.bumptech.glide.request.target.ViewTarget;

import java.util.Map;
import java.util.WeakHashMap;

import io.reist.dali.DaliCallback;
import io.reist.dali.DaliUtils;
import io.reist.dali.ImageLoader;
import io.reist.dali.ImageRequest;
import io.reist.dali.ScaleMode;
import io.reist.dali.drawables.CircleFadingDaliDrawable;
import io.reist.dali.drawables.DaliDrawable;
import io.reist.dali.drawables.FadingDaliDrawable;
import jp.wasabeef.glide.transformations.BlurTransformation;

import static io.reist.dali.DaliUtils.getPlaceholder;
import static io.reist.dali.DaliUtils.setBackground;
import static io.reist.dali.DaliUtils.setDrawable;

/**
 * A loader which uses Glide library to asynchronously load images from the network.
 *
 * Glide bitmap recycling behaviours apply. See
 * https://github.com/bumptech/glide/wiki/Resource-re-use-in-Glide for details.
 */
public class GlideImageLoader implements ImageLoader {

    /**
     * This is to force Glide to generate dummy animations for non-cached images
     */
    private static final ViewPropertyAnimation.Animator EMPTY_ANIMATOR = new ViewPropertyAnimation.Animator() {

        @Override
        public void animate(View view) {}

    };

    private static final int BLUR_RADIUS = 8;
    private static final int BLUR_SAMPLING = 16;

    private final Map<Object, BaseTarget> targetMap = new WeakHashMap<>();

    @Override
    public void load(@NonNull ImageRequest request, @NonNull View view, boolean background) {

        BitmapTypeRequest bitmapTypeRequest = createBitmapTypeRequest(request);

        bitmapTypeRequest.animate(EMPTY_ANIMATOR);

        enqueue(
                view,
                bitmapTypeRequest,
                new GlideImageLoaderViewTarget(
                        view,
                        request.inCircle,
                        background,
                        this,
                        request.scaleMode
                )
        );

    }

    private void enqueue(Object o, BitmapTypeRequest bitmapTypeRequest, BaseTarget<Bitmap> target) {
        targetMap.put(o, target);
        bitmapTypeRequest.into(target);
    }

    @NonNull
    private BitmapTypeRequest createBitmapTypeRequest(ImageRequest builder) {

        RequestManager requestManager;
        Object attachTarget = builder.attachTarget;
        if (attachTarget instanceof android.app.Fragment) {
            requestManager = Glide.with((android.app.Fragment) attachTarget);
        } else if (attachTarget instanceof android.support.v4.app.Fragment) {
            requestManager = Glide.with((android.support.v4.app.Fragment) attachTarget);
        } else if (attachTarget instanceof FragmentActivity) {
            requestManager = Glide.with((FragmentActivity) attachTarget);
        } else if (attachTarget instanceof Activity) {
            requestManager = Glide.with((Activity) attachTarget);
        } else if (attachTarget instanceof Context) {
            requestManager = Glide.with((Context) attachTarget);
        } else {
            throw new IllegalStateException("Attach target is " + attachTarget);
        }

        BitmapTypeRequest bitmapTypeRequest = requestManager.load(builder.url).asBitmap();

        if (builder.placeholderRes != 0) {
            bitmapTypeRequest.placeholder(builder.placeholderRes);
        }

        if (builder.targetWidth > 0 && builder.targetHeight > 0) {
            bitmapTypeRequest.override(builder.targetWidth, builder.targetHeight);
        }

        Context appContext = DaliUtils.getApplicationContext(builder.attachTarget);

        if (appContext == null) {
            throw new IllegalStateException("application context is null");
        }

        if (!builder.disableTransformation) {
            if (builder.blur) {
                bitmapTypeRequest.transform(
                        new OnlyScaleDownTransformation(appContext, builder.scaleMode),
                        new BlurTransformation(appContext, BLUR_RADIUS, BLUR_SAMPLING)
                );
            } else {
                bitmapTypeRequest.transform(
                        new OnlyScaleDownTransformation(appContext, builder.scaleMode)
                );
            }
        }

        if (builder.config != null) {
            bitmapTypeRequest.format(toGlideFormat(builder.config));
        }

        return bitmapTypeRequest;

    }

    @Override
    public void load(@NonNull ImageRequest request, @NonNull DaliCallback callback) {
        enqueue(
                callback,
                createBitmapTypeRequest(request),
                new GlideImageLoaderCallbackTarget(callback, this)
        );
    }

    private static DecodeFormat toGlideFormat(Bitmap.Config config) {
        switch (config) {
            case RGB_565:
                return DecodeFormat.PREFER_RGB_565;
            case ARGB_8888:
                return DecodeFormat.PREFER_ARGB_8888;
            default:
                throw new IllegalArgumentException("Unsupported Bitmap config: " + config);
        }
    }

    @Override
    public void cancel(@NonNull Object o) {
        BaseTarget target = targetMap.get(o);
        if (target != null) {
            Glide.clear(target);
            targetMap.remove(o);
        }
    }

    @Override
    public void cancelAll() {
        for (BaseTarget target : targetMap.values()) {
            Glide.clear(target);
        }
        targetMap.clear();
    }

    /**
     * The circle cropping has known issues with cross-fade transitions in Glide v3.
     * Details can be found on Glide GitHub page, section "Rounded images"
     * (https://github.com/bumptech/glide).
     *
     * TODO remove this class and replace with circleCrop() from Glide v4
     */
    private static class GlideImageLoaderViewTarget extends ViewTarget<View, Bitmap> {

        private final boolean inCircle;
        private final boolean background;
        private final GlideImageLoader loader;
        private final ScaleMode scaleMode;
        private final int dstWidth;
        private final int dstHeight;

        GlideImageLoaderViewTarget(
                View view,
                boolean inCircle,
                boolean background,
                GlideImageLoader loader,
                ScaleMode scaleMode
        ) {

            super(view);

            this.inCircle = inCircle;
            this.background = background;
            this.loader = loader;
            this.scaleMode = scaleMode;

            dstWidth = view.getWidth() - view.getPaddingLeft() - view.getPaddingRight();
            dstHeight = view.getHeight() - view.getPaddingTop() - view.getPaddingBottom();

        }

        @Override
        public void onLoadStarted(Drawable placeholder) {}

        @Override
        public void onLoadFailed(Exception e, Drawable errorDrawable) {
            onImageReady(errorDrawable);
        }

        private void onImageReady(Drawable drawable) {
            if (background) {
                setBackground(drawable, view);
            } else {
                setDrawable(drawable, view);
            }
            loader.targetMap.remove(this);
        }

        @Override
        public void onResourceReady(Bitmap resource, GlideAnimation<? super Bitmap> glideAnimation) {
            Drawable placeholder = getPlaceholder(view, background);
            final boolean noFade = glideAnimation == null || glideAnimation instanceof NoAnimation;
            DaliDrawable drawable;
            if (inCircle) {
                drawable = new CircleFadingDaliDrawable(
                        resource,
                        scaleMode,
                        dstWidth,
                        dstHeight,
                        placeholder,
                        resource.getConfig(),
                        noFade
                );
            } else {
                if (noFade) {
                    drawable = new DaliDrawable(resource, scaleMode, dstWidth, dstHeight);
                } else {
                    drawable = new FadingDaliDrawable(
                            resource,
                            scaleMode,
                            dstWidth,
                            dstHeight,
                            placeholder,
                            resource.getConfig()
                    );
                }
            }
            onImageReady(drawable);
        }

        @Override
        public void getSize(SizeReadyCallback cb) {
           cb.onSizeReady(dstWidth, dstHeight);
        }

    }

    private static class GlideImageLoaderCallbackTarget extends SimpleTarget<Bitmap> {

        private final DaliCallback callback;
        private final GlideImageLoader loader;

        GlideImageLoaderCallbackTarget(DaliCallback callback, GlideImageLoader loader) {
            super();
            this.callback = callback;
            this.loader = loader;
        }

        @Override
        public void onLoadStarted(Drawable placeholder) {}

        @Override
        public void onLoadFailed(Exception e, Drawable errorDrawable) {
            loader.targetMap.remove(this);
        }

        @Override
        public void onResourceReady(Bitmap resource, GlideAnimation<? super Bitmap> glideAnimation) {
            callback.onImageLoaded(resource);
            loader.targetMap.remove(this);
        }

    }

    /**
     * There's no equivalent for Picasso's onlyScaleDown in Glide. To achieve the same effect,
     * here goes BitmapTransformation
     */
    private static class OnlyScaleDownTransformation extends BitmapTransformation {

        static final String ID = OnlyScaleDownTransformation.class.getName();

        private final ScaleMode scaleMode;

        OnlyScaleDownTransformation(Context context, ScaleMode scaleMode) {
            super(context);
            this.scaleMode = scaleMode;
        }

        @Override
        protected Bitmap transform(BitmapPool pool, Bitmap toTransform, int outWidth, int outHeight) {

            Bitmap transformed;

            float scale;

            Bitmap.Config safeConfig = toTransform.getConfig() != null ?
                    toTransform.getConfig() :
                    Bitmap.Config.ARGB_8888;

            switch (scaleMode) {

                case CENTER_CROP:

                    // pick greater scale
                    if (toTransform.getWidth() * outHeight > outWidth * toTransform.getHeight()) {
                        scale = (float) outHeight / (float) toTransform.getHeight();
                    } else {
                        scale = (float) outWidth / (float) toTransform.getWidth();
                    }

                    if (scale < 1f) {
                        final Bitmap toReuse = pool.get(
                                outWidth,
                                outHeight,
                                safeConfig
                        );
                        transformed = TransformationUtils.centerCrop(toReuse, toTransform, outWidth, outHeight);
                        if (toReuse != null && toReuse != transformed && !pool.put(toReuse)) {
                            toReuse.recycle();
                        }
                    } else {
                        transformed = toTransform;
                    }

                    break;

                case CENTER_INSIDE:

                    // pic lesser scale
                    if (toTransform.getWidth() * outHeight > outWidth * toTransform.getHeight()) {
                        scale = (float) outWidth / (float) toTransform.getWidth();
                    } else {
                        scale = (float) outHeight / (float) toTransform.getHeight();
                    }

                    if (scale < 1f) {
                        transformed = TransformationUtils.fitCenter(toTransform, pool, outWidth, outHeight);
                    } else {
                        transformed = toTransform;
                    }

                    break;

                case FIT_XY:

                    // if total number of pixels decreases
                    if (toTransform.getWidth() * toTransform.getHeight() > outWidth * outHeight) {

                        final Bitmap toReuse = pool.get(
                                outWidth,
                                outHeight,
                                safeConfig
                        );
                        transformed = toReuse == null ? Bitmap.createBitmap(
                                outWidth,
                                outHeight,
                                safeConfig
                        ) : toReuse;

                        final Paint paint = new Paint(TransformationUtils.PAINT_FLAGS);
                        final Canvas canvas = new Canvas(transformed);
                        canvas.drawBitmap(
                                toTransform,
                                null,
                                new RectF(0, 0, outWidth, outHeight),
                                paint
                        );

                    } else {
                        transformed = toTransform;
                    }

                    break;

                default:
                    throw new IllegalArgumentException("scaleMode = " + scaleMode);

            }

            return transformed;

        }

        @Override
        public String getId() {
            return ID + "(" + scaleMode.name() + ")";
        }

    }

}