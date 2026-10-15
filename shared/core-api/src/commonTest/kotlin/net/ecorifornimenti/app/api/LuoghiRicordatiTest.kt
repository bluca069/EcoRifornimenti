package net.ecorifornimenti.app.api

import kotlinx.coroutines.test.runTest
import net.ecorifornimenti.app.model.Posizione
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private val FIRENZE = Posizione(43.7696, 11.2558)
private val MILANO = Posizione(45.4642, 9.19)

private fun luogo(nome: String) = Luogo(nome, "Toscana", FIRENZE)

/** Un geocoder finto: conta quante volte lo si interroga. */
private class GeocoderFinto(private val esito: () -> List<Luogo>) : ServizioLuoghi {
    var chiamate = 0
        private set

    override suspend fun cerca(testo: String, vicinoA: Posizione?): List<Luogo> {
        chiamate++
        return esito()
    }
}

/**
 * La memoria volatile dei suggerimenti: quel che si e' gia' chiesto non si richiede.
 * Chi scrive una destinazione cancella e riprova di continuo, e ogni ripensamento non
 * deve costare una domanda a un servizio pubblico.
 */
class LuoghiRicordatiTest {

    @Test
    fun laStessaRicercaNonRichiamaIlServizio() = runTest {
        val geocoder = GeocoderFinto { listOf(luogo("Siena")) }
        val luoghi = LuoghiRicordati(geocoder)

        val primo = luoghi.cerca("siena", FIRENZE)
        val secondo = luoghi.cerca("siena", FIRENZE)
        val terzo = luoghi.cerca("SIENA", FIRENZE)

        assertEquals(primo, secondo)
        assertEquals(primo, terzo)
        assertEquals(1, geocoder.chiamate, "la memoria risponde al posto della rete")
    }

    /**
     * Lo stesso nome cercato da due province diverse e' una domanda diversa: il
     * suggerimento tiene conto di dove ci si trova.
     */
    @Test
    fun zoneDiverseSonoDomandeDiverse() = runTest {
        val geocoder = GeocoderFinto { listOf(luogo("Siena")) }
        val luoghi = LuoghiRicordati(geocoder)

        luoghi.cerca("siena", FIRENZE)
        luoghi.cerca("siena", MILANO)

        assertEquals(2, geocoder.chiamate)
    }

    /** Pochi metri di scarto sono la stessa zona: il GPS si muove da fermo. */
    @Test
    fun unoScartoMinimoRestaLaStessaZona() = runTest {
        val geocoder = GeocoderFinto { listOf(luogo("Siena")) }
        val luoghi = LuoghiRicordati(geocoder)

        luoghi.cerca("siena", FIRENZE)
        luoghi.cerca("siena", Posizione(FIRENZE.lat + 0.001, FIRENZE.lng + 0.001))

        assertEquals(1, geocoder.chiamate)
    }

    @Test
    fun ancheIlVuotoSiRicorda() = runTest {
        val geocoder = GeocoderFinto { emptyList() }
        val luoghi = LuoghiRicordati(geocoder)

        luoghi.cerca("asdfgh", FIRENZE)
        luoghi.cerca("asdfgh", FIRENZE)

        assertEquals(1, geocoder.chiamate, "'non esiste' e' una risposta, e si ricorda")
    }

    /** Un errore non si mette in cache: riprovare deve poter andare meglio. */
    @Test
    fun unErroreNonSiRicorda() = runTest {
        var volte = 0
        val geocoder = GeocoderFinto {
            volte++
            if (volte == 1) throw ErroreRicerca.RispostaInattesa("503") else listOf(luogo("Siena"))
        }
        val luoghi = LuoghiRicordati(geocoder)

        assertFailsWith<ErroreRicerca> { luoghi.cerca("siena", FIRENZE) }
        assertEquals(listOf(luogo("Siena")), luoghi.cerca("siena", FIRENZE))
    }

    @Test
    fun soloDueLettereNonInterroganoNessuno() = runTest {
        val geocoder = GeocoderFinto { listOf(luogo("Siena")) }

        assertTrue(LuoghiRicordati(geocoder).cerca("si", FIRENZE).isEmpty())
        assertEquals(0, geocoder.chiamate)
    }
}
