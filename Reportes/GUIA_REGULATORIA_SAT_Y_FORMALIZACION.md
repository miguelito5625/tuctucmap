# Guía Regulatoria, Fiscal (SAT) y Ruta de Formalización — TucTuc Map

> **Propósito:** Documento estratégico y operativo para determinar los umbrales de crecimiento (conductores, viajes e ingresos) a partir de los cuales TucTuc Map debe atender aspectos regulatorios municipales, tributarios ante la Superintendencia de Administración Tributaria (SAT) y contractuales en Guatemala.

---

## 1. La Gran Ventaja del Modelo de Negocio de TucTuc Map

A diferencia de las plataformas tradicionales de transporte por aplicación (como Uber o InDrive corporativo), donde la empresa procesa el pago del pasajero con tarjeta de crédito, retiene comisiones y dispersa fondos a los choferes, **TucTuc Map opera bajo un modelo de Software como Servicio (SaaS) / Suscripción Tecnológica**:

1. **El pasajero le paga directamente en efectivo al conductor** el valor convenido por el viaje. TucTuc Map no intermedia ni custodia fondos de los pasajeros.
2. **TucTuc Map únicamente cobra al conductor una membresía/suscripción periódica** (diaria, semanal, quincenal o mensual) por el derecho de uso y acceso a la plataforma tecnológica.
3. **Implicación legal y tributaria clave:** La plataforma no actúa como empresa de transporte ni patrono, sino como un **proveedor de herramientas de telecomunicación e intermediación tecnológica** para trabajadores independientes. Esto simplifica radicalmente las obligaciones fiscales y regulatorias iniciales.

---

## 2. El Semáforo de Crecimiento: Métricas y Puntos de Inflexión

| Fase de Crecimiento | Conductores Activos | Viajes Mensuales Estimados | Ingreso Mensual Bruto (Suscripciones) | ¿Preocuparse de la SAT e Impuestos? | ¿Preocuparse de la Municipalidad / PMT? |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Fase 0: Piloto / Pruebas** | **1 a 15** | < 500 viajes | Q0 – Q1,500 / mes | **No.** Enfoque 100% en estabilidad técnica (GPS, estabilidad de la app, feedback). | **Mínimo.** Asegurar únicamente que los conductores de prueba tengan placas y concesión municipal al día. |
| **Fase 1: Validación Comercial** | **15 a 30** | 500 – 1,500 viajes | Q1,500 – Q4,500 / mes | **Vigilancia.** Preparar usuario en Agencia Virtual SAT y validar flujos bancarios. | **Monitoreo gremial.** Escuchar la percepción de los líderes de líneas de mototaxis locales. |
| **Fase 2: Momento Crítico de Formalización** | **30 a 50** | 1,500 – 4,000 viajes | Q4,500 – Q10,000 / mes | **SÍ (Punto de inflexión).** Habilitar Régimen de Pequeño Contribuyente y emitir Factura Electrónica (FEL). | **Contacto preventivo.** Diálogo amigable con directivas de asociaciones de tuctucs para evitar fricciones. |
| **Fase 3: Escala y Consolidación** | **> 50 a 100+** | > 4,000 viajes | > Q12,500 / mes (> Q150k/año) | **SÍ.** Supera el techo de Pequeño Contribuyente. Migración a Régimen General o Sociedad de Emprendimiento (S.E.). | **Formal institucional.** Presentación oficial ante el Concejo Municipal o Juzgado de Asuntos Municipales. |

---

## 3. Análisis de Frentes Regulatorios

### A. Superintendencia de Administración Tributaria (SAT) e Impuestos

* **¿Por qué el disparador son los 30 a 50 conductores y no el número de viajes?**
  * A la SAT y a los bancos no les impacta cuántos viajes se registran en la base de datos de la app. **El disparador real son las transacciones bancarias entrantes**.
  * Con 35 a 50 conductores activos pagando cuotas semanales de Q35 o quincenales de Q70 por transferencia bancaria hacia una cuenta personal, se generan entre **70 y 150 transferencias mensuales de múltiples terceros distintos**.
  * En Guatemala, los bancos del sistema (bajo normativas contra el lavado de dinero de la IVE / SIB y el levantamiento de secreto bancario para fines tributarios según Decreto 19-2016) reportan como atípicas las cuentas personales de ahorro o monetarias que reciben microtransferencias frecuentes y masivas de diferentes remitentes.

* **La solución ágil y económica (Fase 2: 30 a 50 conductores):**
  * **Régimen de Pequeño Contribuyente:** Es el régimen ideal para comenzar.
  * **Tasa impositiva:** Paga únicamente una tarifa plana del **5% definitivo** sobre el total facturado en el mes (ejemplo: sobre Q6,000 recaudados de suscripciones, el impuesto total a pagar son Q300).
  * **Facturación Electrónica en Línea (FEL):** Emisión 100% gratuita desde el portal de la Agencia Virtual de la SAT o la app SAT FEL para cada solicitud de membresía aprobada.
  * **Límite máximo permitido:** Hasta **Q150,000 anuales** (aproximadamente Q12,500 mensuales). No requiere llevar contabilidad completa ni contratar auditoría fiscal.

* **Evolución a Fase 3 (> Q150,000 anuales):**
  * Al superar los Q12,500 mensuales de facturación, se debe evolucionar al **Régimen Opcional Simplificado de ISR** (5% hasta Q30,000 y 7% sobre el excedente) y **Régimen General de IVA**, o bien constituir una **Sociedad de Emprendimiento (S.E.)** o **Sociedad Anónima (S.A.)**.

---

### B. Municipalidades y Policía Municipal de Tránsito (PMT)

En Guatemala, el servicio urbano de mototaxis / tuctucs no está regulado por la Dirección General de Transportes (DGT, la cual aplica a transporte extraurbano), sino de forma autónoma por cada **Municipalidad**, mediante sus reglamentos de tránsito local, Juzgados de Asuntos Municipales y la PMT.

* **El riesgo real:** El riesgo en la calle no suele originarse por inspecciones proactivas de la municipalidad a aplicaciones móviles, sino por **denuncias de los propios gremios y asociaciones de mototaxistas** que sospechen que la app opera vehículos no autorizados ("piratas").
* **Regla de oro preventiva desde el Día 1:**
  * **Solo habilitar conductores con concesión municipal vigente:** Durante el registro y validación del chofer en la plataforma, solicitar fotografía de su calcomanía/número de línea asignado por la municipalidad y su licencia de conducir vigente.
  * Si todos los conductores de TucTuc Map son concesionarios municipales legales, la PMT no tiene causal para decomisar unidades o levantar multas por servicio no autorizado.
* **Estrategia con los gremios:**
  * Cuando se alcancen 25 a 40 conductores, reunirse de forma constructiva con los representantes de las líneas de mototaxis.
  * Presentar la app no como una empresa externa que viene a quitarles pasajeros, sino como una **herramienta de modernización que los protege frente a mototaxis ilegales**, permitiéndoles recibir llamadas directas y optimizar sus turnos.

---

### C. Términos y Condiciones Contractuales (Desde el Lanzamiento Público)

Previo a permitir el registro abierto de usuarios y conductores, la aplicación móvil y la web deben contar con Términos de Servicio y Política de Privacidad claros que contengan:

1. **Cláusula de Intermediación Tecnológica:** Establecer expresamente que *TucTuc Map es una plataforma digital de intermediación tecnológica y comunicación privada entre usuarios, y no presta servicios de transporte, no es propietaria de la flota vehicular ni fija tarifas de forma vinculante*.
2. **Independencia Laboral:** Declarar la inexistencia de subordinación laboral, contrato de trabajo o relación patronal entre la plataforma y los conductores asociados, quienes operan como prestadores independientes con sus propias concesiones.
3. **Carácter Referencial de Tarifas:** Indicar que los precios estimados mostrados en pantalla se calculan con fines orientativos según distancia geodésica y que el precio final del trayecto es acordado y pagado en efectivo de mutuo acuerdo entre pasajero y chofer.

---

## 4. Plan de Acción Recomendado

1. **Hoy (Fase de Pruebas y Estabilidad):**
   - No incurrir en gastos legales ni trámites tributarios prematuros.
   - Probar con 5 a 15 conductores de confianza que cuenten con su concesión municipal en orden.
   - Ajustar telemetría, batería, precisión de mapas y tiempos de respuesta.
2. **Al llegar a 20 conductores:**
   - Verificar acceso a la Agencia Virtual de la SAT y validar requisitos para habilitar emisión FEL.
3. **Al alcanzar 35–50 conductores activos:**
   - Inscribir formalmente la actividad económica como Pequeño Contribuyente (5%).
   - Emitir boletas/facturas electrónicas por cada renovación de suscripción aprobada en el Dashboard.
   - Socializar la plataforma con las directivas de las líneas de tuctucs de la localidad.

