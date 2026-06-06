package com.ejemplo.iot

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ejemplo.iot.databinding.ActivityPrivacyDetailBinding

class PrivacyDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrivacyDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPrivacyDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Aviso de Privacidad"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.textPrivacyContent.text = PRIVACY_NOTICE
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    companion object {
        const val PRIVACY_NOTICE = """
AVISO DE PRIVACIDAD
Monitor de Estrés Ambiental — IoT Sensor Platform
Versión 1.0 | Junio 2026

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. IDENTIDAD DEL RESPONSABLE

Responsables: Ángel Usiel Domínguez López
              y Daniel David Romero Enríquez
Carácter: Proyecto académico
Materia: Tecnologías Emergentes y Transformadoras
Período: Febrero–Junio 2026

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

2. DATOS QUE RECOPILAMOS

- Temperatura ambiental — no personal.
- Nivel de sonido — no personal.
- Detección de presencia (PIR) — POTENCIALMENTE
  PERSONAL: puede inferirse si hay una persona
  en el espacio y sus patrones de comportamiento.
- Nivel de estrés inferido por IA — DATO SENSIBLE:
  inferencia de estado de salud.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

3. FINALIDAD DEL TRATAMIENTO

Primarias (necesarias):
- Monitoreo en tiempo real de condiciones ambientales.
- Detección de estrés ambiental mediante IA.
- Generación de alertas y consejos de bienestar.
- Almacenamiento histórico para mejora del modelo.

Secundarias (opcionales):
- Investigación académica sobre variables ambientales.
- Reportes estadísticos anónimos.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

4. TUS DERECHOS ARCO

- ACCEDER a tus datos almacenados.
- RECTIFICAR datos incorrectos.
- CANCELAR (eliminar) tus datos — disponible en
  Ajustes → Eliminar mis datos.
- OPONERTE al tratamiento para fines secundarios.

Plazo de respuesta: 20 días hábiles (LFPDPPP)
                    30 días (GDPR).
Eliminación de datos: máximo 72 horas.
Retención máxima: 6 meses.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

5. USO DE INTELIGENCIA ARTIFICIAL

- Las recomendaciones de IA son orientativas,
  NO vinculantes ni con efectos legales.
- Sistema clasificado como IA de BAJO RIESGO
  conforme al AI Act (Art. 6).
- Puedes desactivar el análisis de IA en
  cualquier momento desde la app.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

6. TRANSFERENCIAS DE DATOS

- Google Firebase/Firestore: almacenamiento
  cifrado con TLS. DPA vigente bajo GDPR Art. 28.
- APIs de IA: solo valores numéricos de sensores,
  sin datos identificatorios personales.
- Sin transferencias comerciales a terceros.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

7. MARCO LEGAL APLICABLE

- LFPDPPP — Ley Federal de Protección de Datos
  Personales en Posesión de Particulares (México)
- GDPR — Reglamento General de Protección de
  Datos (UE) Reg. 2016/679
- AI Act / NIST AI RMF (EUA/UE)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

© 2026 Ángel Usiel Domínguez López &
        Daniel David Romero Enríquez
Proyecto Académico — Uso no comercial
        """
    }
}