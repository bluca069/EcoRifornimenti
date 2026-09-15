package net.ecorifornimenti.app.api

/**
 * Risposte vere dell'Osservaprezzi, catturate il 14/09/2026 a Milano e Firenze.
 * Sono qui perche' i test difendano il parsing dalle stranezze reali del servizio
 * (address nullo, prezzi doppi self/servito, prodotti premium sullo stesso fuelId),
 * non da un JSON ideale scritto a tavolino.
 */
object RispostaEsempio {

    val zonaMilano = """
    {"success":true,"center":{"lat":45.46,"lng":9.19},"results":[
      {"id":33860,"name":"8051","fuels":[
        {"id":122665443,"price":1.999,"name":"Benzina","fuelId":1,"isSelf":true},
        {"id":122665442,"price":2.099,"name":"Gasolio","fuelId":2,"isSelf":true}],
       "location":{"lat":45.48929604349778,"lng":9.223775554850135},
       "insertDate":"2026-09-14T21:47:04+02:00","address":null,"brand":"Tamoil",
       "distance":"4.188986578651431"},
      {"id":28102,"name":"lingui leonardo 59178","fuels":[
        {"id":122596581,"price":1.985,"name":"Benzina","fuelId":1,"isSelf":false},
        {"id":122596580,"price":1.765,"name":"Benzina","fuelId":1,"isSelf":true},
        {"id":122596579,"price":2.259,"name":"Gasolio","fuelId":2,"isSelf":false},
        {"id":122596578,"price":2.039,"name":"Gasolio","fuelId":2,"isSelf":true},
        {"id":122596577,"price":2.359,"name":"Blue Diesel","fuelId":20,"isSelf":false}],
       "location":{"lat":45.47,"lng":9.20},
       "insertDate":"2026-09-13T22:00:07+02:00","address":null,"brand":"AgipEni",
       "distance":"3.3784862213762232"},
      {"id":99999,"name":"Senza posizione","fuels":[
        {"id":1,"price":1.899,"name":"Benzina","fuelId":1,"isSelf":true}],
       "insertDate":"2026-09-14T10:00:00+02:00","address":null,"brand":"Ignota"},
      {"id":88888,"name":"Senza prezzi","fuels":[],
       "location":{"lat":45.465,"lng":9.195},"address":null,"brand":"Ignota"}
    ]}
    """.trimIndent()

    /** Un impianto lontano dal centro di Milano: serve a verificare il filtro sul raggio. */
    val zonaLontana = """
    {"success":true,"center":{"lat":45.60,"lng":9.19},"results":[
      {"id":70001,"name":"Distributore lontano","fuels":[
        {"id":1,"price":1.599,"name":"Benzina","fuelId":1,"isSelf":true}],
       "location":{"lat":45.75,"lng":9.19},
       "insertDate":"2026-09-14T08:00:00+02:00","address":null,"brand":"Ignota"}
    ]}
    """.trimIndent()

    val zonaVuota = """{"success":true,"center":{"lat":45.46,"lng":9.19},"results":[]}"""

    val dettaglio = """
    {"id":15532,"name":"Fibbi&Valacchi snc","nomeImpianto":"Fibbi&Valacchi snc",
     "address":"VIA SENESE 150 - 50124 FIRENZE (FI)","brand":"Q8",
     "fuels":[
       {"id":122663392,"price":2.029,"name":"Benzina","fuelId":1,"isSelf":false,
        "serviceAreaId":15532,"insertDate":"2026-09-14T18:15:43Z","validityDate":"2026-09-14T18:24:42Z"},
       {"id":122663391,"price":2.029,"name":"Benzina","fuelId":1,"isSelf":true}],
     "phoneNumber":"","email":"","website":"",
     "company":"FIBBI E VALACCHI S.N.C. DI FIBBI PAOLO & C.",
     "services":[{"id":"7","description":"Servizi per disabili"},{"id":"6","description":"Bancomat"}],
     "orariapertura":[
       {"orariAperturaId":1,"giornoSettimanaId":1,"oraAperturaMattina":"07:00",
        "oraChiusuraMattina":"12:30","oraAperturaPomeriggio":"15:00","oraChiusuraPomeriggio":"19:30",
        "flagOrarioContinuato":false,"flagH24":false,"flagChiusura":false,
        "flagNonComunicato":false,"flagSelf":false,"flagServito":true},
       {"orariAperturaId":2,"giornoSettimanaId":2,"oraAperturaOrarioContinuato":"07:00",
        "oraChiusuraOrarioContinuato":"19:30","flagOrarioContinuato":true,"flagH24":false,
        "flagChiusura":false,"flagNonComunicato":false,"flagSelf":false,"flagServito":true},
       {"orariAperturaId":3,"giornoSettimanaId":7,"flagChiusura":true,"flagH24":false,
        "flagOrarioContinuato":false,"flagNonComunicato":false,"flagSelf":false,"flagServito":true},
       {"orariAperturaId":4,"giornoSettimanaId":3,"flagNonComunicato":true,
        "flagOrarioContinuato":false,"flagH24":false,"flagChiusura":false,
        "flagSelf":true,"flagServito":false}
     ]}
    """.trimIndent()
}
