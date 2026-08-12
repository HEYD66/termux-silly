package com.termux.app;

import org.junit.Assert;
import org.junit.Test;

public class STBrowserActivityTest {

    @Test
    public void mobileLayoutUsesMeasuredWebViewHeight() {
        String script = STBrowserActivity.buildMobileLayoutScript(694);

        Assert.assertTrue(script.contains("--stavernWebViewHeight:694px"));
        Assert.assertTrue(script.contains("html,body{height:var(--stavernWebViewHeight)!important"));
        Assert.assertTrue(script.contains("#sheld{left:0!important"));
        Assert.assertTrue(script.contains("height:calc(var(--stavernWebViewHeight) - var(--topBarBlockSize) - 1px)!important"));
        Assert.assertTrue(script.contains("#chat{max-height:calc(var(--stavernWebViewHeight)"));
    }

    @Test
    public void mobileLayoutRejectsNonPositiveHeight() {
        String script = STBrowserActivity.buildMobileLayoutScript(0);

        Assert.assertTrue(script.contains("--stavernWebViewHeight:1px"));
    }
}
