/*
 * Copyright (C) 2017 Renat Sarymsakov.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.reist.dali;

import android.support.annotation.NonNull;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * DeferredImageLoader doesn't perform actual image loading. It just postpones image request until
 * a view is measured to take advantage of optimizations of various Dali implementations. For
 * instance, if a view size is known and this size is relatively small, it's possible to use less
 * memory by storing only scaled-down instances of images.
 *
 * Subclasses must have a public constructor with {@link ImageLoader} parameter.
 *
 * Created by m039 on 12/25/15.
 */
public class DeferredImageLoader implements ImageLoader {

    /**
     * It's ok that the map is never queried because the loading process starts in
     * {@link ViewRequestFactory#ViewRequestFactory(View, ImageRequest, boolean)}.
     * In here, a pre-draw listener is created. The listener requests an image after the
     * attached {@link ImageView} size has its size calculated.
     */
    private final Map<View, ViewRequestFactory> requestMap = new WeakHashMap<>();

    protected static class ViewRequestFactory implements ViewTreeObserver.OnPreDrawListener {

        private ImageRequest imageRequest;
        private final WeakReference<View> target;
        private final boolean background;

        ViewRequestFactory(
                View target,
                ImageRequest imageRequest,
                boolean background
        ) {

            this.imageRequest = imageRequest;
            this.target = new WeakReference<>(target);
            this.background = background;

            ViewTreeObserver viewTreeObserver = target.getViewTreeObserver();
            viewTreeObserver.addOnPreDrawListener(this);

        }

        @Override
        public boolean onPreDraw() {

            View target = this.target.get();

            if (target == null || imageRequest == null) {
                return true;
            }

            try {

                ViewTreeObserver vto = target.getViewTreeObserver();

                try {

                    if (!vto.isAlive()) {
                        return true;
                    }

                    int viewWidth = target.getWidth();
                    int viewHeight = target.getHeight();

                    if (viewWidth <= 0 || viewHeight <= 0) {
                        return true;
                    }

                    if (viewWidth > 0 && viewHeight > 0) {
                        imageRequest.targetSize(
                                viewWidth - target.getPaddingLeft() - target.getPaddingRight(),
                                viewHeight - target.getPaddingTop() - target.getPaddingBottom()
                        );
                    }

                    DaliLoader.getInstance().getMainImageLoader().load(imageRequest, target, background);

                    return true;

                } finally {
                    vto.removeOnPreDrawListener(this);
                    imageRequest = null;
                }

            } finally {
                DaliLoader.getInstance().getDeferredImageLoader().requestMap.remove(target);
            }

        }

        void cancel() {

            View target = this.target.get();

            if (target == null) {
                return;
            }

            try {

                ViewTreeObserver vto = target.getViewTreeObserver();

                vto.removeOnPreDrawListener(this);
                imageRequest = null;

            } finally {
                DaliLoader.getInstance().getDeferredImageLoader().requestMap.remove(target);
            }

        }

    }

    public void defer(View view, ViewRequestFactory viewRequestFactory) {
        requestMap.put(view, viewRequestFactory);
    }

    @Override
    public void load(@NonNull ImageRequest request, @NonNull View view, boolean background) {
        defer(
                view,
                new ViewRequestFactory(
                        view,
                        request,
                        background
                )
        );
    }

    @Override
    public void load(@NonNull ImageRequest request, @NonNull DaliCallback callback) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void cancel(@NonNull Object target) {
        if (target instanceof View) {
            View view = (View) target;
            ViewRequestFactory viewRequestFactory = requestMap.remove(view);
            if (viewRequestFactory != null) {
                viewRequestFactory.cancel();
            }
        }
    }

    @Override
    public void cancelAll() {
        Collection<ViewRequestFactory> values = requestMap.values();
        for (ViewRequestFactory factory : values) {
            factory.cancel();
        }
        requestMap.clear();
    }

}
