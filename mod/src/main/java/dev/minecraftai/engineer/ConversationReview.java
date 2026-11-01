package dev.minecraftai.engineer;

/** Chooses among actual server-held proposals, never from model prose or last-message timing. */
enum ConversationReview {
    NONE,ACTION,BEHAVIOR,STRUCTURAL;
    static ConversationReview choose(boolean action,boolean behavior,boolean structural){
        if((action?1:0)+(behavior?1:0)+(structural?1:0)>1)throw new IllegalStateException("More than one proposal is pending. Cancel the pending proposals and ask again so your approval has one clear meaning.");
        return action?ACTION:behavior?BEHAVIOR:structural?STRUCTURAL:NONE;
    }
}
