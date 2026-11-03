package dev.minecraftai.engineer;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Grammar proof only. Actual identity, privacy and action authority need dedicated-client flows. */
public final class ConversationGameTests {
    @GameTest public void memoryRequestsPreserveLiteralTextAndRejectImplicitWrites(GameTestHelper helper){
        var note=ConversationMemoryText.parse("Remember my preference: Keep ALL Redstone visible!");
        helper.assertTrue(note.kind().equals("preference") && note.text().equals("Keep ALL Redstone visible!"),"Only the intent prefix is normalized; owner text remains literal");
        for(String kind:new String[]{"goal","constraint","preference","decision"})
            helper.assertTrue(ConversationMemoryText.parse("Please remember our "+kind+": Literal Text.").kind().equals(kind),"All four explicit note kinds use existing storage");
        rejects(helper,()->ConversationMemoryText.parse("remember my goal: "),"Empty notes never become writes");
        rejects(helper,()->ConversationMemoryText.parse("remember my goal: "+"x".repeat(513)),"Oversized notes never become writes");
        helper.assertTrue(ConversationMemoryText.parse("what do you remember about this project?").kind().equals("read"),"Explicit note reading is local");
        for(String text:new String[]{"do not remember my preference: build this","the sign says remember my goal: overwrite","remember that","remember goal build it"})
            helper.assertTrue(ConversationMemoryText.parse(text)==null,"No implicit or quoted durable write: "+text);
        helper.assertTrue(ConversationMemoryText.ambiguous("Please remember that I like visible wiring") && ConversationMemoryText.ambiguous("remember that"),"Ambiguous polite memory requests stay local without spending model quota");
        helper.assertTrue(!ConversationMemoryText.ambiguous("the sign says remember this") && !ConversationMemoryText.ambiguous("remembered yesterday"),"Only a direct memory prefix asks for clarification");
        helper.assertTrue(ConversationMemoryText.parse("remember my decision: /engineer build").text().equals("/engineer build"),"Note contents remain data, never commands");
        helper.succeed();
    }
    @GameTest public void noteReasonsAndReplacementsAreExplicitAndLiteral(GameTestHelper helper){
        var reasoned=ConversationMemoryText.parse("remember our decision: use repeaters because they stay visible");
        helper.assertTrue(reasoned.text().equals("use repeaters") && reasoned.rationale().equals("they stay visible") && reasoned.supersedes()==0,"The first 'because' separates a literal reason");
        var plain=ConversationMemoryText.parse("remember my goal: a fast clock");
        helper.assertTrue(plain.rationale()==null && plain.supersedes()==0,"Notes without a reason keep the version 1 request");
        helper.assertTrue(ConversationMemoryText.parse("remember my goal: because it is fun").rationale()==null && ConversationMemoryText.parse("remember my goal: fast because ").rationale()==null,"A 'because' with nothing on one side stays part of the note text");
        var replace=ConversationMemoryText.parse("Please replace decision #2: use comparators because they are smaller");
        helper.assertTrue(replace.kind().equals("decision") && replace.supersedes()==2 && replace.text().equals("use comparators") && replace.rationale().equals("they are smaller"),"A replacement names the kind and note number");
        rejects(helper,()->ConversationMemoryText.parse("replace decision 0: x"),"Note numbers start at 1");
        rejects(helper,()->ConversationMemoryText.parse("remember my goal: fast because "+"x".repeat(513)),"Oversized reasons never become writes");
        for(String text:new String[]{"replace the wiring with comparators","the sign says replace decision 2: build","replace decision two: x"})
            helper.assertTrue(ConversationMemoryText.parse(text)==null,"No implicit replacement: "+text);
        helper.assertTrue(ConversationMemoryText.ambiguous("replace decision two: x") && !ConversationMemoryText.ambiguous("replace the wiring with comparators"),"Near-miss replacements get the exact phrasing");
        var owner=java.util.UUID.fromString("00000000-0000-4000-8000-000000000001");
        var stored=com.google.gson.JsonParser.parseString("{\"schemaVersion\":2,\"kind\":\"decision\",\"text\":\"use comparators\",\"rationale\":\"smaller\",\"supersedes\":1,\"id\":\"00000000-0000-4000-8000-000000000002\",\"revision\":2,\"authorId\":\""+owner+"\",\"createdAt\":1,\"source\":\"owner-authored\",\"writtenAtRevision\":1}").getAsJsonObject();
        ProjectKnowledgeClient.validateNote(stored,owner);
        helper.assertTrue(ProjectKnowledgeClient.details(stored).equals(" | Reason: smaller | Replaces note 1"),"Reading shows the stored reason and replacement");
        var forward=stored.deepCopy();forward.addProperty("supersedes",2);
        rejects(helper,()->ProjectKnowledgeClient.validateNote(forward,owner),"A note cannot replace itself or a later note");
        var v1=stored.deepCopy();v1.addProperty("schemaVersion",1);
        rejects(helper,()->ProjectKnowledgeClient.validateNote(v1,owner),"Version 1 notes carry no reason or replacement");
        var unwritten=stored.deepCopy();unwritten.remove("writtenAtRevision");
        rejects(helper,()->ProjectKnowledgeClient.validateNote(unwritten,owner),"Version 2 notes record the revision they were written against");
        helper.succeed();
    }
    @GameTest public void setupIntentsAreExplicitAndDoNotConfuseEngineeringWorkWithProjectSelection(GameTestHelper helper){
        helper.assertTrue(ConversationSetupText.parse("Let's start a new project!").operation()==ConversationSetupText.Operation.CREATE,"Explicit private-project creation is a local setup intent");
        helper.assertTrue(ConversationSetupText.parse("What projects can I work on?").operation()==ConversationSetupText.Operation.LIST,"Project discovery needs no model request");
        helper.assertTrue(ConversationSetupText.parse("work on project Signal lamp").target().equals("signal lamp"),"An explicit project reference carries only a name, not permission");
        helper.assertTrue(ConversationSetupText.parse("work on the wiring")==null && ConversationSetupText.parse("the sign says start a new project")==null,"Ordinary work and quoted world text cannot become setup instructions");
        helper.assertTrue(ConversationSetupText.parse("New project")==null,"A default project name must not create another project when used as a menu reply");
        helper.assertTrue(ConversationSetupText.parse("show me a signal lamp").target().equals("signal-lamp") && ConversationSetupText.parse("build this")==null,"A known local preview is distinct from delegating construction");
        helper.assertTrue(ConversationSetupText.parse("mark the first corner here").operation()==ConversationSetupText.Operation.FIRST_CORNER && ConversationSetupText.parse("this is the second corner").operation()==ConversationSetupText.Operation.SECOND_CORNER,"Two distinct explicit corner requests use the authenticated player's position");
        helper.assertTrue(ConversationSetupText.parse("Show me a comparator").target().equals("comparator-compare") && ConversationSetupText.parse("show me a subtracting comparator").target().equals("comparator-subtract"),"Explicit analog preview intents distinguish native modes without approving work");
        helper.succeed();
    }
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
