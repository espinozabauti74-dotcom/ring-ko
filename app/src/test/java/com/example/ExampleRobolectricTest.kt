package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Ring KO", appName)
  }

  @Test
  fun `fighter roster has five unique contenders including brandon`() {
    val fighters = com.example.model.FighterRoster.fighters
    assertEquals(5, fighters.size)
    assertEquals(5, fighters.map { it.id }.toSet().size)
    val brandon = com.example.model.FighterRoster.byId("brandon")
    assertEquals("Brandon", brandon.name)
    assertEquals("La gran muralla", brandon.nickname)
    val mateo = com.example.model.FighterRoster.byId("mateo")
    assertEquals("El Rulos", mateo.nickname)
    assertEquals("DIFÍCIL", mateo.difficultyLabel)
    val benja = com.example.model.FighterRoster.byId("bruno")
    assertEquals("Benja Rodríguez", benja.name)
    assertEquals("El Black", benja.nickname)
    assertEquals("CAMPEÓN", benja.difficultyLabel)
    assertEquals(androidx.compose.ui.graphics.Color(0xFF141418), benja.gloveColor)
    val bautista = com.example.model.FighterRoster.byId("diego")
    assertEquals("Bautista Espinoza", bautista.name)
    assertEquals("LEYENDA", bautista.difficultyLabel)
  }

  @Test
  fun `glove shop catalog, skins, and 10 rounds are configured`() {
    val gloves = com.example.model.GloveShopCatalog.gloves
    assertEquals(5, gloves.size)
    assertEquals(0, gloves.first().priceCoins)
    val initialState = com.example.model.FightUiState()
    assertEquals(10, initialState.maxRounds)
    assertEquals(com.example.model.FightPhase.PRESENTATION, initialState.phase)
    assertEquals(6, com.example.model.BoxerSkinCatalog.presets.size)
    val customSkin = com.example.model.BoxerAppearance(skinToneIndex = 3, hairColorIndex = 2, trunksColorIndex = 4)
    assertEquals(com.example.model.BoxerSkinCatalog.skinTones[3].color, customSkin.resolvedSkinColor())
    assertEquals(com.example.model.BoxerSkinCatalog.hairColors[2].color, customSkin.resolvedHairColor())
    assertEquals(com.example.model.BoxerSkinCatalog.trunksColors[4].color, customSkin.resolvedTrunksColor())
  }

  @Test
  fun `sound engine handles punch block dodge and referee announcements safely`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val soundEngine = com.example.audio.BoxingSoundEngine(context)
    soundEngine.soundEnabled = true
    soundEngine.hapticsEnabled = false
    soundEngine.playPresentationFanfare()
    soundEngine.playBell(doubleRing = true)
    soundEngine.playPunchThrow(com.example.model.PunchType.JAB)
    soundEngine.playPunchThrow(com.example.model.PunchType.UPPERCUT)
    soundEngine.playImpact(heavy = true, counter = true, punchType = com.example.model.PunchType.HOOK)
    soundEngine.playGuardUp()
    soundEngine.playBlock(perfectParry = true)
    soundEngine.playGuardBreak()
    soundEngine.playDodge(perfect = false)
    soundEngine.playDodge(perfect = true)
    soundEngine.announceReferee("¡Box!")
    soundEngine.announceRefereeCount(5)
    soundEngine.stopReferee()
    soundEngine.release()
  }

  @Test
  fun `mqtt codec encodes and decodes publish and connack packets accurately`() {
    val connectBytes = com.example.chat.MqttCodec.encodeConnect("ringko_test_1")
    assertEquals(0x10, connectBytes[0].toInt() and 0xFF)

    val subBytes = com.example.chat.MqttCodec.encodeSubscribe(7, "ringko/v1/room/ABC123")
    assertEquals(0x82, subBytes[0].toInt() and 0xFF)

    val pubBytes = com.example.chat.MqttCodec.encodePublish("ringko/v1/room/ABC123", "{\"type\":\"CHAT\",\"text\":\"Hola\"}")
    val connAck = byteArrayOf(0x20, 0x02, 0x00, 0x00)
    val combined = connAck + pubBytes

    // Verify partial buffer stops cleanly, then full buffer decodes both packets
    val (partialDecoded, partialConsumed) = com.example.chat.MqttCodec.decodePackets(combined.copyOf(combined.size - 3))
    assertEquals(1, partialDecoded.size)
    assertEquals(connAck.size, partialConsumed)

    val (fullDecoded, fullConsumed) = com.example.chat.MqttCodec.decodePackets(combined)
    assertEquals(combined.size, fullConsumed)
    assertEquals(2, fullDecoded.size)
    assertEquals(com.example.chat.MqttDecodedPacket.ConnAck(0), fullDecoded[0])
    val pub = fullDecoded[1] as com.example.chat.MqttDecodedPacket.Publish
    assertEquals("ringko/v1/room/ABC123", pub.topic)
    assertEquals("{\"type\":\"CHAT\",\"text\":\"Hola\"}", pub.payload)
  }

  @Test
  fun `chat arena viewmodel handles code normalization, incoming messages, and 1v1 challenge flow without account`() {
    val code = com.example.chat.ChatCodeGenerator.generateRoomCode()
    assertEquals(6, code.length)
    assertEquals("K7M2P9", com.example.chat.ChatCodeGenerator.normalizeCode(" k7m-2p9 "))

    val context = ApplicationProvider.getApplicationContext<Context>()
    val vm = com.example.chat.ChatArenaViewModel(
      soundEngine = com.example.audio.BoxingSoundEngine(context),
      soundEnabled = { false },
      hapticsEnabled = { false }
    )

    // Simulate incoming friend joining and sending a chat message
    vm.handleProtocolJson(
      org.json.JSONObject().apply {
        put("type", "JOIN")
        put("senderId", "friend_1")
        put("name", "Lucas")
        put("charId", "bruno")
        put("gloveId", "obsidian_king")
      }
    )
    vm.handleProtocolJson(
      org.json.JSONObject().apply {
        put("type", "CHAT")
        put("senderId", "friend_1")
        put("msgId", "m1")
        put("senderName", "Lucas")
        put("text", "¿Listo para pelear?")
        put("ts", 1000L)
      }
    )
    val stateAfterMsg = vm.chatState.value
    assertEquals(1, stateAfterMsg.members.size)
    assertEquals("Lucas", stateAfterMsg.members.first().name)
    assertEquals(true, stateAfterMsg.messages.any { it.text == "¿Listo para pelear?" })

    // Simulate incoming challenge & accepting into the ring
    vm.handleProtocolJson(
      org.json.JSONObject().apply {
        put("type", "CHALLENGE")
        put("senderId", "friend_1")
        put("challengeId", "ch_1")
        put("name", "Lucas")
        put("rounds", 3)
        put("charId", "bruno")
        put("gloveId", "obsidian_king")
      }
    )
    assertEquals("ch_1", vm.chatState.value.incomingChallenge?.challengeId)

    vm.acceptIncomingChallenge()
    assertEquals(null, vm.chatState.value.incomingChallenge)
    assertEquals(true, vm.chatState.value.activeBoutId != null)
    assertEquals(com.example.online.OnlineBoutPhase.PRESENTATION, vm.fightState.value.phase)
    assertEquals("Lucas", vm.fightState.value.oppName)

    vm.exitFightToChat()
    assertEquals(null, vm.chatState.value.activeBoutId)
  }
}
