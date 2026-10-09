package cn.happyoyster.opensdk.demo.app

import cn.happyoyster.opensdk.demo.gateway.ScriptAct
import cn.happyoyster.opensdk.demo.gateway.ScriptListPayload
import cn.happyoyster.opensdk.demo.gateway.ScriptSubject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptListDraftsTest {
    @Test fun rejectsDraftsThatTheServerRejects() {
        val valid = ScriptListPayload(
            subjects = listOf(ScriptSubject(label = "[character_1]", type = "character")),
            acts = (1..45).map { ScriptAct(it, "[character_1] speaks") },
        )
        assertTrue(ScriptListDrafts.isValidCreateDraft(ScriptListDrafts.encode(valid)))
        assertFalse(ScriptListDrafts.isValidCreateDraft(ScriptListDrafts.encode(
            valid.copy(subjects = listOf(ScriptSubject(label = "[character_7]"))),
        )))
        assertFalse(ScriptListDrafts.isValidCreateDraft(ScriptListDrafts.encode(
            valid.copy(acts = valid.acts.toMutableList().apply { set(0, ScriptAct(1, "x".repeat(2001))) }),
        )))
    }
}
