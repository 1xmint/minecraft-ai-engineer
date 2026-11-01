package dev.minecraftai.engineer;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Grammar proof only. Actual identity, privacy and action authority need dedicated-client flows. */
public final class ConversationGameTests {
    @GameTest public void ordinaryChatCannotBecomeAnEngineeringRequest(GameTestHelper helper){
        for(String ordinary:new String[]{"hello Bob","Engineer is my favorite job","Tell Engineer, build this","/engineer request yes","not @engineer yes","somebody said: Engineer, yes"})
            helper.assertTrue(ConversationText.addressed(ordinary)==null,"Only explicitly addressed chat can enter the bot: "+ordinary);
        for(String addressed:new String[]{"Engineer, explain how this works"," ENGINEER: explain how this works ","@Engineer explain how this works","Hey Engineer, explain how this works"})
            helper.assertTrue(ConversationText.addressed(addressed).equals("explain how this works"),"Addressing preserves the original request, without command execution");
        helper.assertTrue(ConversationText.addressed("Engineer,").isEmpty(),"An empty greeting is distinct from ordinary chat");
        rejects(helper,()->ConversationText.addressed("Engineer, "+"x".repeat(1001)),"Addressed input stays bounded");
        rejects(helper,()->ConversationText.addressed("Engineer, yes\nrun something else"),"Control characters cannot smuggle a second request");helper.succeed();
    }
    @GameTest public void conversationIntentsDoNotContainAuthority(GameTestHelper helper){
        helper.assertTrue(ConversationText.lesson("Explain how this works!").equals("explain this part again"),"Natural detail questions map to a read-only lesson topic");
        helper.assertTrue(ConversationText.lesson("What materials do I need?").equals("explain the materials"),"Material intent is a design question, not inventory access");
        helper.assertTrue(ConversationText.lesson("Build this now")==null,"Construction must go through actual scoped interpretation and delegation");
        helper.assertTrue(ConversationText.yes("Yes, do it") && ConversationText.yes("yes please!") && !ConversationText.yes("yes but change the design"),"Explicit approval is distinct from a request to change the reviewed action");
        helper.assertTrue(!ConversationText.yes("the sign says yes") && ConversationText.no("Never mind."),"Quoted world text does not establish consent");helper.succeed();
    }
    @GameTest public void approvalMustIdentifyOneActualReviewAndStopCannotBecomeApproval(GameTestHelper helper){
        helper.assertTrue(ConversationReview.choose(false,false,false)==ConversationReview.NONE,"No pending proposal cannot become approval");
        helper.assertTrue(ConversationReview.choose(true,false,false)==ConversationReview.ACTION && ConversationReview.choose(false,true,false)==ConversationReview.BEHAVIOR && ConversationReview.choose(false,false,true)==ConversationReview.STRUCTURAL,"Each distinct server-held proposal has its own handler");
        for(boolean[] pair:new boolean[][]{{true,true,false},{true,false,true},{false,true,true},{true,true,true}}){boolean rejected=false;try{ConversationReview.choose(pair[0],pair[1],pair[2]);}catch(IllegalStateException expected){rejected=true;}helper.assertTrue(rejected,"A vague yes cannot silently choose between multiple reviews");}
        helper.assertTrue(ConversationText.stop("Please stop!") && ConversationText.stop("hold on") && !ConversationText.stop("the sign says stop") && !ConversationText.yes("stop"),"Explicit stop remains distinct from approval or quoted world text");helper.succeed();
    }
    private static void rejects(GameTestHelper helper,Runnable action,String message){boolean rejected=false;try{action.run();}catch(IllegalArgumentException expected){rejected=true;}helper.assertTrue(rejected,message);}
}
