package net.carmgate.morph.ui.common;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface RenderingHints {
	RenderingSteps renderingStep();
}
