/*
 * Copyright (C) 2007-2015 FBReader.ORG Limited <contact@fbreader.org>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 */

package org.geometerplus.fbreader.fbreader;

import org.geometerplus.fbreader.bookmodel.BookModel;
import org.geometerplus.fbreader.bookmodel.FBHyperlinkType;
import org.geometerplus.fbreader.bookmodel.TOCTree;
import org.geometerplus.fbreader.fbreader.options.ColorProfile;
import org.geometerplus.fbreader.fbreader.options.FooterOptions;
import org.geometerplus.fbreader.fbreader.options.ImageOptions;
import org.geometerplus.fbreader.fbreader.options.MiscOptions;
import org.geometerplus.fbreader.fbreader.options.PageTurningOptions;
import org.geometerplus.fbreader.fbreader.options.ViewOptions;
import org.geometerplus.fbreader.util.FixedTextSnippet;
import org.geometerplus.fbreader.util.TextSnippet;
import org.geometerplus.zlibrary.core.filesystem.ZLFile;
import org.geometerplus.zlibrary.core.filesystem.ZLResourceFile;
import org.geometerplus.zlibrary.core.fonts.FontEntry;
import org.geometerplus.zlibrary.core.library.ZLibrary;
import org.geometerplus.zlibrary.core.util.ZLColor;
import org.geometerplus.zlibrary.core.view.SelectionCursor;
import org.geometerplus.zlibrary.core.view.ZLPaintContext;
import org.geometerplus.zlibrary.text.model.ZLTextModel;
import org.geometerplus.zlibrary.text.view.ExtensionElementManager;
import org.geometerplus.zlibrary.text.view.ZLTextHighlighting;
import org.geometerplus.zlibrary.text.view.ZLTextHyperlink;
import org.geometerplus.zlibrary.text.view.ZLTextHyperlinkRegionSoul;
import org.geometerplus.zlibrary.text.view.ZLTextImageRegionSoul;
import org.geometerplus.zlibrary.text.view.ZLTextPosition;
import org.geometerplus.zlibrary.text.view.ZLTextRegion;
import org.geometerplus.zlibrary.text.view.ZLTextVideoRegionSoul;
import org.geometerplus.zlibrary.text.view.ZLTextView;
import org.geometerplus.zlibrary.text.view.ZLTextWordCursor;
import org.geometerplus.zlibrary.text.view.ZLTextWordRegionSoul;
import org.geometerplus.zlibrary.text.view.style.ZLTextStyleCollection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public final class FBView extends ZLTextView {

    public static final int SCROLLBAR_SHOW_AS_FOOTER = 3;
    public static final int SCROLLBAR_SHOW_AS_FOOTER_OLD_STYLE = 4;
    private final FBReaderApp myReader;
    private final ViewOptions myViewOptions;
    private final BookElementManager myBookElementManager;
    private int myStartY;
    private boolean myIsBrightnessAdjustmentInProgress;
    private int myStartBrightness;

    private TapZoneMap myZoneMap;
    private Footer myFooter;
    /**
     * 是否显示放大镜
     */
    private boolean mCanMagnifier = false;

    FBView(FBReaderApp reader) {
        super(reader);
        myReader = reader;
        myViewOptions = reader.ViewOptions;
        myBookElementManager = new BookElementManager(this);
    }

    public void setModel(ZLTextModel model) {
        super.setModel(model);
        if (myFooter != null) {
            myFooter.resetTOCMarks();
        }
    }

    @Override
    protected ExtensionElementManager getExtensionManager() {
        return myBookElementManager;
    }

    @Override
    protected void releaseSelectionCursor() {
        super.releaseSelectionCursor();
        if (getCountOfSelectedWords() > 0) {
            myReader.runAction(ActionCode.SELECTION_SHOW_PANEL);
        }
    }

    @Override
    public synchronized void onScrollingFinished(PageIndex pageIndex) {
        super.onScrollingFinished(pageIndex);
        if (myReader.PageTurningOptions.Animation.getValue() == Animation.previewNone) {
            // 恢复原来的动画
            myReader.PageTurningOptions.Animation.setValue(Animation.previewShift);
        }
        myReader.storePosition();
    }

    @Override
    public int scrollbarType() {
        return myViewOptions.ScrollbarType.getValue();
    }

    @Override
    protected String getPageProgress() {
        StringBuilder info = new StringBuilder();
        PagePosition pagePosition = pagePosition();
        info.append(pagePosition.Current);
        info.append(" / ");
        info.append(pagePosition.Total);
        return info.toString();
    }

    @Override
    protected ZLPaintContext.ColorAdjustingMode getAdjustingModeForImages() {
        if (myReader.ImageOptions.MatchBackground.getValue()) {
            if (ColorProfile.THEME_WHITE.equals(myViewOptions.getColorProfile().Name)) {
                return ZLPaintContext.ColorAdjustingMode.DARKEN_TO_BACKGROUND;
            } else {
                return ZLPaintContext.ColorAdjustingMode.LIGHTEN_TO_BACKGROUND;
            }
        } else {
            return ZLPaintContext.ColorAdjustingMode.NONE;
        }
    }

    @Override
    public String getTOCText(ZLTextWordCursor cursor) {
        int index = cursor.getParagraphIndex();
        if (cursor.isEndOfParagraph()) {
            ++index;
        }
        TOCTree treeToSelect = null;
        for (TOCTree tree : myReader.Model.TOCTree) {
            final TOCTree.Reference reference = tree.getReference();
            if (reference == null) {
                continue;
            }
            if (reference.ParagraphIndex > index) {
                break;
            }
            treeToSelect = tree;
        }
        return treeToSelect == null ? "" : treeToSelect.getText();
    }

    @Override
    protected ZLColor getExtraColor() {
        ColorProfile profile = myViewOptions.getColorProfile();
        return profile.HeaderAndFooterColorOption.getValue();
    }

    @Override
    public ImageFitting getImageFitting() {
        return myReader.ImageOptions.FitToScreen.getValue();
    }

    @Override
    public int getTopMargin() {
        return myViewOptions.TopMargin.getValue();
    }

    @Override
    public int getBottomMargin() {
        return myViewOptions.BottomMargin.getValue();
    }

    @Override
    public int getSpaceBetweenColumns() {
        return myViewOptions.SpaceBetweenColumns.getValue();
    }

    @Override
    public ZLFile getWallpaperFile() {
        final String filePath = myViewOptions.getColorProfile().WallpaperOption.getValue();
        if ("".equals(filePath)) {
            return null;
        }

        final ZLFile file = ZLFile.createFileByPath(filePath);
        if (file == null || !file.exists()) {
            return null;
        }
        return file;
    }

    @Override
    public ZLPaintContext.FillMode getFillMode() {
        return getWallpaperFile() instanceof ZLResourceFile
                ? ZLPaintContext.FillMode.tileMirror
                : myViewOptions.getColorProfile().FillModeOption.getValue();
    }

    @Override
    public ZLColor getBackgroundColor() {
        return myViewOptions.getColorProfile().BackgroundOption.getValue();
    }

    @Override
    public ZLColor getBookMarkColor() {
        return myViewOptions.getColorProfile().BookMarkColorOption.getValue();
    }

    @Override
    public ZLColor getSelectionBackgroundColor() {
        return myViewOptions.getColorProfile().SelectionBackgroundOption.getValue();
    }

    @Override
    public ZLColor getSelectionCursorColor() {
        return myViewOptions.getColorProfile().SelectionCursorOption.getValue();
    }

    @Override
    public ZLColor getSelectionForegroundColor() {
        return myViewOptions.getColorProfile().SelectionForegroundOption.getValue();
    }

    /**
     * 获取文件颜色
     *
     * @param hyperlink 超链接
     * @return 文字颜色
     */
    @Override
    public ZLColor getTextColor(ZLTextHyperlink hyperlink) {
        final ColorProfile profile = myViewOptions.getColorProfile();
        switch (hyperlink.Type) {
            default:
            case FBHyperlinkType.NONE:
                return profile.RegularTextOption.getValue();
            case FBHyperlinkType.INTERNAL:
            case FBHyperlinkType.FOOTNOTE:
                return myReader.Collection.isHyperlinkVisited(myReader.getCurrentBook(), hyperlink.Id)
                        ? profile.VisitedHyperlinkTextOption.getValue()
                        : profile.HyperlinkTextOption.getValue();
            case FBHyperlinkType.EXTERNAL:
                return profile.HyperlinkTextOption.getValue();
        }
    }

    @Override
    public boolean twoColumnView() {
        return getContextHeight() <= getContextWidth() && myViewOptions.TwoColumnView.getValue();
    }

    @Override
    public int getLeftMargin() {
        return myViewOptions.LeftMargin.getValue();
    }

    @Override
    public int getRightMargin() {
        return myViewOptions.RightMargin.getValue();
    }

    @Override
    public ZLTextStyleCollection getTextStyleCollection() {
        return myViewOptions.getTextStyleCollection();
    }

    @Override
    public ZLColor getHighlightingBackgroundColor() {
        return myViewOptions.getColorProfile().HighlightingBackgroundOption.getValue();
    }

    @Override
    public ZLColor getHighlightingForegroundColor() {
        return myViewOptions.getColorProfile().HighlightingForegroundOption.getValue();
    }

    @Override
    public Footer getFooterArea() {
        switch (myViewOptions.ScrollbarType.getValue()) {
            case SCROLLBAR_SHOW_AS_FOOTER:
                if (!(myFooter instanceof FooterNewStyle)) {
                    if (myFooter != null) {
                        myReader.removeTimerTask(myFooter.UpdateTask);
                    }
                    myFooter = new FooterNewStyle();
                    myReader.addTimerTask(myFooter.UpdateTask, 15000);
                }
                break;
            case SCROLLBAR_SHOW_AS_FOOTER_OLD_STYLE:
                if (!(myFooter instanceof FooterOldStyle)) {
                    if (myFooter != null) {
                        myReader.removeTimerTask(myFooter.UpdateTask);
                    }
                    myFooter = new FooterOldStyle();
                    myReader.addTimerTask(myFooter.UpdateTask, 15000);
                }
                break;
            default:
                if (myFooter != null) {
                    myReader.removeTimerTask(myFooter.UpdateTask);
                    myFooter = null;
                }
                break;
        }
        return myFooter;
    }

    @Override
    public Animation getAnimationType() {
        return myReader.PageTurningOptions.Animation.getValue();
    }

    /**
     * 手指按下状态
     *
     * @param x x坐标
     * @param y y坐标
     */
    @Override
    public void onFingerPress(int x, int y) {
        // 隐藏Toast
        myReader.runAction(ActionCode.HIDE_TOAST);

        final float maxDist = ZLibrary.Instance().getDisplayDPI() / 4f;
        final SelectionCursor.Which cursor = findSelectionCursor(x, y, maxDist * maxDist);
        if (cursor != null) {
            myReader.runAction(ActionCode.SELECTION_HIDE_PANEL);
            moveSelectionCursorTo(cursor, x, y);
            return;
        }

        // 如果允许屏幕亮度调节（手势），并且按下位置在内容宽度的 1 / 10，
        // --> (1). 标识屏幕亮度调节，(2). 记录起始Y，(3). 记录当前屏幕亮度
        if (myReader.MiscOptions.AllowScreenBrightnessAdjustment.getValue() && x < getContextWidth() / 10) {
            myIsBrightnessAdjustmentInProgress = true;
            myStartY = y;
            myStartBrightness = myReader.getViewWidget().getScreenBrightness();
            return;
        }

        // 开启手动滑动模式
        startManualScrolling(x, y);
    }

    /**
     * 开启手动滑动模式
     *
     * @param x x坐标
     * @param y y坐标
     */
    private void startManualScrolling(int x, int y) {
        // 如果滑动翻页不可用，直接返回
        if (!isFlickScrollingEnabled()) {
            return;
        }

        // 获取翻页的方向
        final boolean horizontal = myReader.PageTurningOptions.Horizontal.getValue();
        final Direction direction = horizontal ? Direction.rightToLeft : Direction.up;
        myReader.getViewWidget().startManualScrolling(x, y, direction);
    }

    /**
     * 滑动翻页是否可用
     * {@link org.geometerplus.fbreader.fbreader.options.PageTurningOptions.FingerScrollingType#byFlick}
     * {@link org.geometerplus.fbreader.fbreader.options.PageTurningOptions.FingerScrollingType#byTapAndFlick}
     *
     * @return 滑动翻页是否可用
     */
    private boolean isFlickScrollingEnabled() {
        final PageTurningOptions.FingerScrollingType fingerScrolling = myReader.PageTurningOptions.FingerScrolling.getValue();
        return fingerScrolling == PageTurningOptions.FingerScrollingType.byFlick ||
                fingerScrolling == PageTurningOptions.FingerScrollingType.byTapAndFlick;
    }

    @Override
    public void onFingerRelease(int x, int y) {
        mCanMagnifier = false;
        final SelectionCursor.Which cursor = getSelectionCursorInMovement();
        if (cursor != null) {
            releaseSelectionCursor();
        }
        // 如果有选中，恢复选中动作弹框
        if (myReader.isActionEnabled(ActionCode.SELECTION_CLEAR)) {
            myReader.runAction(ActionCode.SELECTION_SHOW_PANEL);
            return;
        }
        if (cursor != null) {
            releaseSelectionCursor();
        } else if (myIsBrightnessAdjustmentInProgress) {
            myIsBrightnessAdjustmentInProgress = false;
        } else if (isFlickScrollingEnabled()) {
            myReader.getViewWidget().startAnimatedScrolling(
                    x, y, myReader.PageTurningOptions.AnimationSpeed.getValue()
            );
        }
    }

    @Override
    public void onFingerMove(int x, int y) {

        final SelectionCursor.Which cursor = getSelectionCursorInMovement();
        if (cursor != null) {
            mCanMagnifier = true;
            moveSelectionCursorTo(cursor, x, y);
            return;
        }

        // 如果有选中， 隐藏选中动作弹框
        if (myReader.isActionEnabled(ActionCode.SELECTION_CLEAR)) {
            myReader.runAction(ActionCode.SELECTION_HIDE_PANEL);
            return;
        }

        synchronized (this) {
            if (myIsBrightnessAdjustmentInProgress) {
                if (x >= getContextWidth() / 5) {
                    myIsBrightnessAdjustmentInProgress = false;
                    startManualScrolling(x, y);
                } else {
                    final int delta = (myStartBrightness + 30) * (myStartY - y) / getContextHeight();
                    myReader.getViewWidget().setScreenBrightness(myStartBrightness + delta);
                    return;
                }
            }

            if (isFlickScrollingEnabled()) {
                myReader.getViewWidget().scrollManuallyTo(x, y);
            }
        }
    }

    @Override
    public boolean onFingerLongPress(int x, int y) {
        myReader.runAction(ActionCode.HIDE_TOAST);

        // 预览模式不处理
        if (isPreview()) {
            return true;
        }

        // 如果有选中， 隐藏选中动作弹框
        if (myReader.isActionEnabled(ActionCode.SELECTION_CLEAR)) {
            myReader.runAction(ActionCode.SELECTION_HIDE_PANEL);
            return true;
        }

        mCanMagnifier = true;

        final ZLTextRegion region = findRegion(x, y, maxSelectionDistance(), ZLTextRegion.AnyRegionFilter);
        if (region != null) {
            final ZLTextRegion.Soul soul = region.getSoul();
            boolean doSelectRegion = false;
            if (soul instanceof ZLTextWordRegionSoul) {
                switch (myReader.MiscOptions.WordTappingAction.getValue()) {
                    case startSelecting:
                        myReader.runAction(ActionCode.SELECTION_HIDE_PANEL);
                        initSelection(x, y);
                        final SelectionCursor.Which cursor = findSelectionCursor(x, y);
                        if (cursor != null) {
                            moveSelectionCursorTo(cursor, x, y);
                        }
                        return true;
                    case selectSingleWord:
                    case openDictionary:
                        doSelectRegion = true;
                        break;
                }
            } else if (soul instanceof ZLTextImageRegionSoul) {
                doSelectRegion =
                        myReader.ImageOptions.TapAction.getValue() !=
                                ImageOptions.TapActionEnum.doNothing;
            } else if (soul instanceof ZLTextHyperlinkRegionSoul) {
                doSelectRegion = true;
            }

            if (doSelectRegion) {
                outlineRegion(region);
                myReader.getViewWidget().reset();
                myReader.getViewWidget().repaint();
                return true;
            }
        }
        return false;
    }

    @Override
    public void onFingerReleaseAfterLongPress(int x, int y) {
        mCanMagnifier = false;
        final SelectionCursor.Which cursor = getSelectionCursorInMovement();
        if (cursor != null) {
            releaseSelectionCursor();
            return;
        }

        // 如果有选中， 显示选中动作弹框
        if (myReader.isActionEnabled(ActionCode.SELECTION_CLEAR)) {
            myReader.runAction(ActionCode.SELECTION_SHOW_PANEL);
            return;
        }

        final ZLTextRegion region = getOutlinedRegion();
        if (region != null) {
            final ZLTextRegion.Soul soul = region.getSoul();

            boolean doRunAction = false;
            if (soul instanceof ZLTextWordRegionSoul) {
                doRunAction =
                        myReader.MiscOptions.WordTappingAction.getValue() ==
                                MiscOptions.WordTappingActionEnum.openDictionary;
            } else if (soul instanceof ZLTextImageRegionSoul) {
                doRunAction =
                        myReader.ImageOptions.TapAction.getValue() ==
                                ImageOptions.TapActionEnum.openImageView;
            }

            if (doRunAction) {
                myReader.runAction(ActionCode.PROCESS_HYPERLINK);
            }
        }
    }

    @Override
    public void onFingerMoveAfterLongPress(int x, int y) {
        final SelectionCursor.Which cursor = getSelectionCursorInMovement();
        if (cursor != null) {
            moveSelectionCursorTo(cursor, x, y);
            return;
        }

        ZLTextRegion region = getOutlinedRegion();
        if (region != null) {
            ZLTextRegion.Soul soul = region.getSoul();
            if (soul instanceof ZLTextHyperlinkRegionSoul ||
                    soul instanceof ZLTextWordRegionSoul) {
                if (myReader.MiscOptions.WordTappingAction.getValue() !=
                        MiscOptions.WordTappingActionEnum.doNothing) {
                    region = findRegion(x, y, maxSelectionDistance(), ZLTextRegion.AnyRegionFilter);
                    if (region != null) {
                        soul = region.getSoul();
                        if (soul instanceof ZLTextHyperlinkRegionSoul
                                || soul instanceof ZLTextWordRegionSoul) {
                            outlineRegion(region);
                            myReader.getViewWidget().reset();
                            myReader.getViewWidget().repaint();
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onFingerSingleTap(int x, int y) {

        // 预览模式的情况下，点击为打开菜单
        if (isPreview()) {
            myReader.runAction(ActionCode.SHOW_MENU, x, y);
            return;
        }

        // 如果有选中，则(1). 清除选中，(2). 隐藏选中动作弹框
        if (myReader.isActionEnabled(ActionCode.SELECTION_CLEAR)) {
            myReader.runAction(ActionCode.SELECTION_CLEAR);
            myReader.runAction(ActionCode.SELECTION_HIDE_PANEL);
            return;
        }

        final ZLTextRegion hyperlinkRegion = findRegion(x, y, maxSelectionDistance(), ZLTextRegion.HyperlinkFilter);
        if (hyperlinkRegion != null) {
            outlineRegion(hyperlinkRegion);
            myReader.getViewWidget().reset();
            myReader.getViewWidget().repaint();
            myReader.runAction(ActionCode.PROCESS_HYPERLINK);
            return;
        }

        final ZLTextRegion bookRegion = findRegion(x, y, 0, ZLTextRegion.ExtensionFilter);
        if (bookRegion != null) {
            myReader.runAction(ActionCode.DISPLAY_BOOK_POPUP, bookRegion);
            return;
        }

        final ZLTextRegion videoRegion = findRegion(x, y, 0, ZLTextRegion.VideoFilter);
        if (videoRegion != null) {
            outlineRegion(videoRegion);
            myReader.getViewWidget().reset();
            myReader.getViewWidget().repaint();
            myReader.runAction(ActionCode.OPEN_VIDEO, (ZLTextVideoRegionSoul) videoRegion.getSoul());
            return;
        }

        final ZLTextHighlighting highlighting = findHighlighting(x, y, maxSelectionDistance());
        if (highlighting instanceof BookmarkHighlighting) {
            myReader.runAction(
                    ActionCode.SELECTION_BOOKMARK,
                    ((BookmarkHighlighting) highlighting).Bookmark
            );
            return;
        }

        if (myReader.isActionEnabled(ActionCode.HIDE_TOAST)) {
            myReader.runAction(ActionCode.HIDE_TOAST);
            return;
        }

        onFingerSingleTapLastResort(x, y);
    }

    private void onFingerSingleTapLastResort(int x, int y) {
        myReader.runAction(getZoneMap().getActionByCoordinates(
                x, y, getContextWidth(), getContextHeight(),
                isDoubleTapSupported() ? TapZoneMap.Tap.singleNotDoubleTap : TapZoneMap.Tap.singleTap
        ), x, y);
    }

    private TapZoneMap getZoneMap() {
        final PageTurningOptions prefs = myReader.PageTurningOptions;
        String id = prefs.TapZoneMap.getValue();
        if ("".equals(id)) {
            id = prefs.Horizontal.getValue() ? "right_to_left" : "up";
        }
        if (myZoneMap == null || !id.equals(myZoneMap.Name)) {
            myZoneMap = TapZoneMap.zoneMap(id);
        }
        return myZoneMap;
    }

    @Override
    public void onFingerDoubleTap(int x, int y) {
        myReader.runAction(ActionCode.HIDE_TOAST);

        myReader.runAction(getZoneMap().getActionByCoordinates(
                x, y, getContextWidth(), getContextHeight(), TapZoneMap.Tap.doubleTap
        ), x, y);
    }

    @Override
    public void onFingerEventCancelled() {
        final SelectionCursor.Which cursor = getSelectionCursorInMovement();
        if (cursor != null) {
            releaseSelectionCursor();
        }
    }

    @Override
    public boolean isDoubleTapSupported() {
        return myReader.MiscOptions.EnableDoubleTap.getValue();
    }

    public boolean onTrackballRotated(int diffX, int diffY) {
        if (diffX == 0 && diffY == 0) {
            return true;
        }

        final Direction direction = (diffY != 0) ?
                (diffY > 0 ? Direction.down : Direction.up) :
                (diffX > 0 ? Direction.leftToRight : Direction.rightToLeft);

        new MoveCursorAction(myReader, direction).run();
        return true;
    }

    @Override
    public boolean canMagnifier() {
        return mCanMagnifier;
    }

    @Override
    public boolean hasSelection() {
        return myReader.isActionEnabled(ActionCode.SELECTION_CLEAR);
    }

    @Override
    public boolean isHorizontal() {
        return myReader.PageTurningOptions.Horizontal.getValue();
    }

    public int getCountOfSelectedWords() {
        final WordCountTraverser traverser = new WordCountTraverser(this);
        if (!isSelectionEmpty()) {
            traverser.traverse(getSelectionStartPosition(), getSelectionEndPosition());
        }
        return traverser.getCount();
    }

    public TextSnippet getSelectedSnippet() {
        final ZLTextPosition start = getSelectionStartPosition();
        final ZLTextPosition end = getSelectionEndPosition();
        if (start == null || end == null) {
            return null;
        }
        final TextBuildTraverser traverser = new TextBuildTraverser(this);
        traverser.traverse(start, end);
        return new FixedTextSnippet(start, end, traverser.getText());
    }

    private abstract class Footer implements FooterArea {
        protected ArrayList<TOCTree> myTOCMarks;
        private Runnable UpdateTask = new Runnable() {
            public void run() {
                myReader.getViewWidget().repaint();
            }
        };
        private int myMaxTOCMarksNumber = -1;
        private List<FontEntry> myFontEntry;
        private Map<String, Integer> myHeightMap = new HashMap<String, Integer>();
        private Map<String, Integer> myCharHeightMap = new HashMap<String, Integer>();

        public int getHeight() {
            return myViewOptions.FooterHeight.getValue();
        }

        public synchronized void resetTOCMarks() {
            myTOCMarks = null;
        }

        protected synchronized void updateTOCMarks(BookModel model, int maxNumber) {
            if (myTOCMarks != null && myMaxTOCMarksNumber == maxNumber) {
                return;
            }

            myTOCMarks = new ArrayList<>();
            myMaxTOCMarksNumber = maxNumber;

            TOCTree toc = model.TOCTree;
            if (toc == null) {
                return;
            }
            int maxLevel = Integer.MAX_VALUE;
            if (toc.getSize() >= maxNumber) {
                final int[] sizes = new int[10];
                for (TOCTree tocItem : toc) {
                    if (tocItem.Level < 10) {
                        ++sizes[tocItem.Level];
                    }
                }
                for (int i = 1; i < sizes.length; ++i) {
                    sizes[i] += sizes[i - 1];
                }
                for (maxLevel = sizes.length - 1; maxLevel >= 0; --maxLevel) {
                    if (sizes[maxLevel] < maxNumber) {
                        break;
                    }
                }
            }
            for (TOCTree tocItem : toc.allSubtrees(maxLevel)) {
                myTOCMarks.add(tocItem);
            }
        }

        protected String buildInfoString(PagePosition pagePosition, String separator) {
            final StringBuilder info = new StringBuilder();
            final FooterOptions footerOptions = myViewOptions.getFooterOptions();

            if (footerOptions.showProgressAsPages()) {
                maybeAddSeparator(info, separator);
                info.append(pagePosition.Current);
                info.append("/");
                info.append(pagePosition.Total);
            }
            if (footerOptions.showProgressAsPercentage() && pagePosition.Total != 0) {
                maybeAddSeparator(info, separator);
                info.append(100 * pagePosition.Current / pagePosition.Total);
                info.append("%");
            }

            if (footerOptions.ShowClock.getValue()) {
                maybeAddSeparator(info, separator);
                info.append(ZLibrary.Instance().getCurrentTimeString());
            }
            if (footerOptions.ShowBattery.getValue()) {
                maybeAddSeparator(info, separator);
                info.append(myReader.getBatteryLevel());
                info.append("%");
            }
            return info.toString();
        }

        private void maybeAddSeparator(StringBuilder info, String separator) {
            if (info.length() > 0) {
                info.append(separator);
            }
        }

        protected synchronized int setFont(ZLPaintContext context, int height, boolean bold) {
            final String family = myViewOptions.getFooterOptions().Font.getValue();
            if (myFontEntry == null || !family.equals(myFontEntry.get(0).Family)) {
                myFontEntry = Collections.singletonList(FontEntry.systemEntry(family));
            }
            final String key = family + (bold ? "N" : "B") + height;
            final Integer cached = myHeightMap.get(key);
            if (cached != null) {
                context.setFont(myFontEntry, cached, bold, false, false, false);
                final Integer charHeight = myCharHeightMap.get(key);
                return charHeight != null ? charHeight : height;
            } else {
                int h = height + 2;
                int charHeight = height;
                final int max = height < 9 ? height - 1 : height - 2;
                for (; h > 5; --h) {
                    context.setFont(myFontEntry, h, bold, false, false, false);
                    charHeight = context.getCharHeight('H');
                    if (charHeight <= max) {
                        break;
                    }
                }
                myHeightMap.put(key, h);
                myCharHeightMap.put(key, charHeight);
                return charHeight;
            }
        }
    }

    private class FooterOldStyle extends Footer {
        public synchronized void paint(ZLPaintContext context) {
            final ZLFile wallpaper = getWallpaperFile();
            if (wallpaper != null) {
                context.clear(wallpaper, getFillMode());
            } else {
                context.clear(getBackgroundColor());
            }

            final BookModel model = myReader.Model;
            if (model == null) {
                return;
            }

            //final ZLColor bgColor = getBackgroundColor();
            // TODO: separate color option for footer color
            final ZLColor fgColor = getTextColor(ZLTextHyperlink.NO_LINK);
            final ZLColor fillColor = myViewOptions.getColorProfile().FooterFillOption.getValue();

            final int left = getLeftMargin();
            final int right = context.getWidth() - getRightMargin();
            final int height = getHeight();
            final int lineWidth = height <= 10 ? 1 : 2;
            final int delta = height <= 10 ? 0 : 1;
            setFont(context, height, height > 10);

            final PagePosition pagePosition = FBView.this.pagePosition();

            // draw info text
            final String infoString = buildInfoString(pagePosition, " ");
            final int infoWidth = context.getStringWidth(infoString);
            context.setTextColor(fgColor);
            context.drawString(right - infoWidth, height - delta, infoString);

            // draw gauge
            final int gaugeRight = right - (infoWidth == 0 ? 0 : infoWidth + 10);
            final int gaugeWidth = gaugeRight - left - 2 * lineWidth;

            context.setLineColor(fgColor);
            context.setLineWidth(lineWidth);
            context.drawLine(left, lineWidth, left, height - lineWidth);
            context.drawLine(left, height - lineWidth, gaugeRight, height - lineWidth);
            context.drawLine(gaugeRight, height - lineWidth, gaugeRight, lineWidth);
            context.drawLine(gaugeRight, lineWidth, left, lineWidth);

            final int gaugeInternalRight =
                    left + lineWidth + (int) (1.0 * gaugeWidth * pagePosition.Current / pagePosition.Total);

            context.setFillColor(fillColor);
            context.fillRectangle(left + 1, height - 2 * lineWidth, gaugeInternalRight, lineWidth + 1);

            final FooterOptions footerOptions = myViewOptions.getFooterOptions();
            if (footerOptions.ShowTOCMarks.getValue()) {
                updateTOCMarks(model, footerOptions.MaxTOCMarks.getValue());
                final int fullLength = sizeOfFullText();
                for (TOCTree tocItem : myTOCMarks) {
                    TOCTree.Reference reference = tocItem.getReference();
                    if (reference != null) {
                        final int refCoord = sizeOfTextBeforeParagraph(reference.ParagraphIndex);
                        final int xCoord =
                                left + 2 * lineWidth + (int) (1.0 * gaugeWidth * refCoord / fullLength);
                        context.drawLine(xCoord, height - lineWidth, xCoord, lineWidth);
                    }
                }
            }
        }
    }

    private class FooterNewStyle extends Footer {
        public synchronized void paint(ZLPaintContext context) {
            final ColorProfile cProfile = myViewOptions.getColorProfile();
            context.clear(cProfile.FooterNGBackgroundOption.getValue());

            final BookModel model = myReader.Model;
            if (model == null) {
                return;
            }

            final ZLColor textColor = cProfile.FooterNGForegroundOption.getValue();
            final ZLColor readColor = cProfile.FooterNGForegroundOption.getValue();
            final ZLColor unreadColor = cProfile.FooterNGForegroundUnreadOption.getValue();

            final int left = getLeftMargin();
            final int right = context.getWidth() - getRightMargin();
            final int height = getHeight();
            final int lineWidth = height <= 12 ? 1 : 2;
            final int charHeight = setFont(context, height * 3 / 5, height > 12);

            final PagePosition pagePosition = FBView.this.pagePosition();

            // draw info text
            final String infoString = buildInfoString(pagePosition, "  ");
            final int infoWidth = context.getStringWidth(infoString);
            context.setTextColor(textColor);
            context.drawString(right - infoWidth, (height + charHeight + 1) / 2, infoString);

            // draw gauge
            final int gaugeRight = right - (infoWidth == 0 ? 0 : infoWidth + 10);
            final int gaugeInternalRight =
                    left + (int) (1.0 * (gaugeRight - left) * pagePosition.Current / pagePosition.Total + 0.5);
            final int v = height / 2;

            context.setLineWidth(lineWidth);
            context.setLineColor(readColor);
            // context.drawLine(left, v, gaugeInternalRight, v);
            if (gaugeInternalRight < gaugeRight) {
                context.setLineColor(unreadColor);
                context.drawLine(gaugeInternalRight + 1, v, gaugeRight, v);
            }

            // draw labels
            final FooterOptions footerOptions = myViewOptions.getFooterOptions();
            if (footerOptions.ShowTOCMarks.getValue()) {
                final TreeSet<Integer> labels = new TreeSet<Integer>();
                labels.add(left);
                labels.add(gaugeRight);
                updateTOCMarks(model, footerOptions.MaxTOCMarks.getValue());
                final int fullLength = sizeOfFullText();
                for (TOCTree tocItem : myTOCMarks) {
                    TOCTree.Reference reference = tocItem.getReference();
                    if (reference != null) {
                        final int refCoord = sizeOfTextBeforeParagraph(reference.ParagraphIndex);
                        labels.add(left + (int) (1.0 * (gaugeRight - left) * refCoord / fullLength + 0.5));
                    }
                }
                for (int l : labels) {
                    context.setLineColor(l <= gaugeInternalRight ? readColor : unreadColor);
                    context.drawLine(l, v + 3, l, v - lineWidth - 2);
                }
            }
        }
    }
}
