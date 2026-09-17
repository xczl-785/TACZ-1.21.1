package dev.weaponassemblyui.client;

/** Pure layout calculation for the adaptive workbench attachment chooser. */
final class ChooserLayout {
    static final int PADDING=10;
    static final int MAX_COLUMNS=5;
    static final int MAX_VISIBLE_ROWS=5;
    static final int SCROLLER_WIDTH=12;

    record Metrics(int candidateCount,int columns,int rows,int visibleRows,int width,int height,
                   int contentWidth,int contentHeight,int candidateTop,boolean scrollable) {}

    static Metrics forCount(int candidateCount) {
        if(candidateCount<0)throw new IllegalArgumentException("candidateCount must be non-negative");
        int cell=WorkbenchSlotMetrics.CHOOSER_CELL,gap=WorkbenchSlotMetrics.CHOOSER_GAP;
        int columns=candidateCount==0?1:Math.min(MAX_COLUMNS,(int)Math.ceil(Math.sqrt(candidateCount)));
        int rows=candidateCount==0?0:(candidateCount+columns-1)/columns;
        int visibleRows=Math.min(rows,MAX_VISIBLE_ROWS);
        boolean scrollable=rows>MAX_VISIBLE_ROWS;
        int contentWidth=columns*cell+(columns-1)*gap;
        int contentHeight=rows==0?0:rows*cell+(rows-1)*gap;
        int candidateTop=PADDING+cell+gap;
        int width=PADDING*2+contentWidth+(scrollable?SCROLLER_WIDTH:0);
        int height=PADDING*2+cell+(candidateCount==0?0:gap+visibleRows*cell+(visibleRows-1)*gap);
        return new Metrics(candidateCount,columns,rows,visibleRows,width,height,contentWidth,contentHeight,
                candidateTop,scrollable);
    }

    private ChooserLayout() {}
}
