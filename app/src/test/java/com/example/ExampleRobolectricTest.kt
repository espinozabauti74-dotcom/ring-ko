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
}
