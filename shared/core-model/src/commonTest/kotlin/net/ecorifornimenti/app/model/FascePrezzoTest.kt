package net.ecorifornimenti.app.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun impianto(id: Int, prezzo: Double) = Impianto(
    id = id,
    nome = "Impianto $id",
    bandiera = "",
    posizione = Posizione(45.0, 9.0),
    prezzi = listOf(PrezzoCarburante(fuelId = 1, nome = "Benzina", prezzo = prezzo, self = true)),
)

class FascePrezzoTest {

    private val pref = PreferenzaRicerca(TipoCarburante.BENZINA, ModalitaErogazione.SELF)

    @Test
    fun `divide gli impianti attorno alla mediana`() {
        val impianti = listOf(
            impianto(1, 1.700),
            impianto(2, 1.900),
            impianto(3, 1.905),
            impianto(4, 2.200),
        )
        val f = fasce(impianti, pref)

        assertEquals(FasciaPrezzo.CONVENIENTE, f[1])
        assertEquals(FasciaPrezzo.MEDIA, f[2])
        assertEquals(FasciaPrezzo.MEDIA, f[3])
        assertEquals(FasciaPrezzo.CARO, f[4])
    }

    @Test
    fun `un solo impianto non e' ne' conveniente ne' caro`() {
        assertEquals(FasciaPrezzo.MEDIA, fasce(listOf(impianto(1, 1.9)), pref)[1])
    }

    @Test
    fun `un fuori scala non sposta il giudizio sugli altri`() {
        // La mediana e' robusta: un impianto a 5 euro non deve far sembrare
        // convenienti tutti gli altri.
        val conOutlier = fasce(
            listOf(impianto(1, 1.899), impianto(2, 1.900), impianto(3, 1.901), impianto(4, 5.0)),
            pref,
        )
        assertEquals(FasciaPrezzo.MEDIA, conOutlier[1])
        assertEquals(FasciaPrezzo.CARO, conOutlier[4])
    }

    @Test
    fun `chi non eroga il carburante resta fuori dalla classifica`() {
        val senzaBenzina = Impianto(
            id = 9, nome = "Solo gasolio", bandiera = "", posizione = Posizione(45.0, 9.0),
            prezzi = listOf(PrezzoCarburante(fuelId = 2, nome = "Gasolio", prezzo = 1.8, self = true)),
        )
        val f = fasce(listOf(impianto(1, 1.9), senzaBenzina), pref)
        assertTrue(9 !in f)
    }

    @Test
    fun `lista vuota non esplode`() {
        assertTrue(fasce(emptyList(), pref).isEmpty())
    }
}

class PreferenzaRicercaTest {

    /**
     * Cosa trova chi apre l'app per la prima volta. E' una scelta di prodotto, non un
     * dettaglio: cambiarla va fatto apposta, non per inerzia di un refactoring.
     */
    @Test
    fun `alla prima apertura si parte da benzina self a dieci km`() {
        val iniziale = PreferenzaRicerca()
        assertEquals(TipoCarburante.BENZINA, iniziale.tipo)
        assertEquals(ModalitaErogazione.SELF, iniziale.modalita)
        assertEquals(10, iniziale.raggioKm)
    }

    @Test
    fun `i raggi offerti partono da cinque km`() {
        assertEquals(listOf(5, 10, 15, 25), PreferenzaRicerca.RAGGI_KM)
        // Il predefinito resta 10: e' il raggio piu' ampio che il servizio copre con
        // una sola chiamata.
        assertEquals(10, PreferenzaRicerca().raggioKm)
    }

    @Test
    fun `i codici carburante sono quelli attesi dall'API`() {
        assertEquals("2-1", PreferenzaRicerca(TipoCarburante.GASOLIO, ModalitaErogazione.SELF).fuelType)
        assertEquals("1-0", PreferenzaRicerca(TipoCarburante.BENZINA, ModalitaErogazione.SERVITO).fuelType)
        assertEquals("4-x", PreferenzaRicerca(TipoCarburante.GPL, ModalitaErogazione.INDIFFERENTE).fuelType)
    }
}
