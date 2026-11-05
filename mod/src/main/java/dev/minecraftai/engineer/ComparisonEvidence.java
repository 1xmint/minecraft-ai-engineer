package dev.minecraftai.engineer;

/**
 * The wording that keeps a design comparison honest: what each measurement means, what a recommendation
 * rests on and what it does not show, and how imported geometry is described. Read-only text; it never
 * changes a design, selects work or labels anything as tested.
 */
final class ComparisonEvidence {
    private ComparisonEvidence() { }

    static final String DEFINITIONS="Definitions: blocks = planned block positions; items = the total of the saved material list, or one per planned block when none is saved"
            +" (wall torches count as torches); length and width = the longer and shorter horizontal sides of the box around the planned blocks; height = that box's"
            +" vertical size, all in blocks; footprint = length×width in blocks²; volume = length×width×height of that box in blocks³, empty space included.";
    static final String IMPORTED_BEHAVIOR="imported schematic geometry with no defined behavior";
    static final String IMPORTED_STATUS="imported geometry, untested; it has no behavior contract, so no game test can show that it works";

    static boolean imported(Blueprint b){
        return b!=null && ImportedBlueprint.describes(b.source());
    }

    /** What the recommendation rests on, per design, and what it cannot show. */
    static String basis(DesignComparison.Measure a,DesignComparison.Measure b){
        return "Evidence and limits: the measurements come from the saved plans, not from the built world. "+evidence(a)+" "+evidence(b)
                +" Fewer blocks or materials is not evidence that a design works, is faster or is more reliable.";
    }

    private static String evidence(DesignComparison.Measure m){
        if(m.imported())return m.label()+" is imported geometry and untested.";
        return m.label()+(m.currentPass()?" has a passed game test of its current revision.":" has no passed game test of its current revision.");
    }
}
