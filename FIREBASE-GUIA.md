# GUIA DE FIREBASE PARA SOCIOFINANZAS

Instrucciones para dejar la sincronizacion entre moviles funcionando.
Escrito para seguir paso a paso, sin conocimientos previos de Firebase.

---

## 0. Antes de empezar: comprobar el proyecto

Hay que saber si el proyecto de Firebase que usa la app es tuyo o de Google.

1. Abre https://console.firebase.google.com
2. Inicia sesion con tu cuenta de Google (la misma que usas para la app)
3. Mira la lista de proyectos

- **Aparece un proyecto que empieza por `ai-studio-android-sociofin`**
  -> Es tuyo. Sigue desde el paso 2.
- **No aparece nada parecido**
  -> El proyecto es de Google y no puedes gestionarlo. Ve al paso 1B.

---

## 1B. Crear un proyecto propio (si el anterior no es tuyo)

Solo si en el paso 0 no encontraste el proyecto.

1. En la consola, pulsa **Crear proyecto**
2. Nombre: `SocioFinanzas`
3. Google Analytics: se puede desactivar (no hace falta)
4. Pulsa **Crear proyecto** y espera

### Anadir la app Android

1. En la pantalla del proyecto, pulsa el icono **Android**
2. **Nombre del paquete** (debe ser exacto):
   ```
   com.aistudio.sociofinanzas.kvyrtw
   ```
3. **Huella SHA-1**:
   ```
   17:67:00:C2:29:AC:E3:AF:3B:C9:D9:56:74:FB:C4:CF:B2:5A:7D:A5
   ```
4. **Registrar app**
5. **Descargar google-services.json**

### Sustituir el archivo en GitHub

1. Abre tu `google-services.json` con el Bloc de notas (Ctrl+A, Ctrl+C)
2. Ve a https://github.com/Nilexpat/app-control-de-finanzas/settings/secrets/actions
3. **GOOGLE_SERVICES_JSON** -> **Update** -> pega el contenido nuevo -> guardar

### Crear la base de datos de Firestore

1. Menu lateral: **Compilacion -> Firestore Database**
2. **Crear base de datos**
3. Elige **modo de produccion** (las reglas se ponen en el paso 3)
4. Ubicacion: la mas cercana (por ejemplo `us-central1` o `southamerica-east1`)

> ATENCION: al crear un proyecto nuevo, la app apunta a una base de datos
> llamada `ai-studio-android-sociofin-f5c4d9c9-4d8d-45b8-8b46-ebee3a314cda`.
> Esa base con nombre personalizado NO existe en un proyecto nuevo. Hay que
> editarla en `app/src/main/res/values/firebase_applet_config.xml` y dejar el
> valor `(default)`, o cambiar el codigo para usar la base por defecto.

---

## 2. Activar el inicio de sesion con Google

Necesario para que funcione el login y el emparejamiento.

1. Menu lateral: **Compilacion -> Authentication**
2. **Comenzar**
3. Pestana **Sign-in method**
4. **Google** -> lapiz -> **Habilitar** -> correo de asistencia -> **Guardar**

---

## 3. Publicar las reglas de seguridad

El archivo `firestore.rules` del repositorio contiene las reglas.

1. En la consola: **Firestore Database -> pestana Reglas**
2. Borra todo el contenido del editor
3. Abre `firestore.rules` del repositorio, copia TODO y pegalo
4. Pulsa **Publicar**

### Verificar que no te has bloqueado

Despues de publicar, comprueba que la app sigue funcionando:

1. Abre la app en el movil
2. Registra una venta
3. En la consola: **Firestore Database -> Datos**
4. Deberias ver:
   ```
   groups/{algo}/movements/{algo}
   ```

- **Si aparece** -> las reglas funcionan. Sigue al paso 4.
- **Si NO aparece y en la app hay errores de permisos** -> vuelve a la pestana
  Reglas y restaura las anteriores. Avisa y se corrigen.

> Con estas reglas solo los miembros de una empresa (los UID que figuran en su
> campo `memberUids`) pueden leer o escribir sus datos, y la unica forma de
> entrar es aceptar una invitacion vigente. Tres cosas a tener en cuenta:
>
> - **Antes de publicar**, ejecuta `probar-reglas.cmd`: prueba las reglas en un
>   emulador local, sin tocar tu proyecto. Tiene que terminar con `0 fallos`.
> - **Antes de publicar**, revisa en **Datos -> groups** que `memberUids` de cada
>   empresa contiene el UID de todos sus socios activos. Quien no figure pierde
>   el acceso (se arregla anadiendo su UID a mano en la consola).
> - Las reglas van emparejadas con la version de la app: publica las reglas y
>   actualiza la app en todos los moviles a la vez. Las invitaciones creadas con
>   la version anterior dejan de servir; hay que generar otras.
>
> Limitacion conocida: dentro de una empresa las reglas no distinguen
> administrador de socio; eso lo sigue aplicando la app.

---

## 4. Crear los indices de Firestore

Firestore necesita indices para las dos consultas que usa la sincronizacion.
Sin ellos, las consultas fallan con un error que incluye un enlace directo.

### Como crearlos

1. Abre la app y registra un movimiento, o intenta unirte con un codigo
2. Mira los registros de la app (o la consola de Firebase -> Firestore -> Uso)
3. Si falta un indice, el error trae un enlace que empieza por:
   ```
   https://console.firebase.google.com/.../indexes?create_composite=...
   ```
4. Pulsalo, y en la pantalla que se abre pulsa **Crear indice**
5. Espera unos minutos a que pase de "Creando" a "Habilitado"

### Indices necesarios

| Coleccion | Campos | Consulta |
|---|---|---|
| `groups` | `memberUids` (array) + `__name__` (ascendente) | Empresas a las que pertenece el usuario |
| `invitations` | `code` (ascendente) | Buscar la invitacion por codigo |
| `invitations` | `groupId` (ascendente) | Listar invitaciones de un grupo |

---

## 5. Probar la sincronizacion entre dos moviles

### Preparacion

- Movil A: tu cuenta de Google
- Movil B: la cuenta de tu amigo

### Prueba 1: compartir un movimiento

1. En el movil A, crea una empresa y registra una venta
2. En la consola de Firestore, comprueba que el movimiento aparece
3. En el movil B, inicia sesion y comprueba que **ves la empresa y la venta**

### Prueba 2: unirse por codigo desde otro movil

1. En el movil A: **Grupo -> Invitar** -> copia el codigo de 8 caracteres
2. En el movil B: **Grupo -> Unirse con Codigo** -> pega el codigo
3. En el movil B deberia aparecer la empresa completa, con sus movimientos

### Prueba 3: ida y vuelta

1. En el movil B, registra una compra
2. En el movil A deberia aparecer en segundos, sin recargar

---

## 6. Si algo no funciona

### La app funciona pero los datos no llegan al otro movil

Comprueba en este orden:

1. **Firestore tiene datos?** Consola -> Firestore Database -> Datos.
   Si esta vacio, las escrituras fallan: mira los registros de la app.
2. **Faltan indices?** Ver paso 4.
3. **Faltan permisos?** Ver paso 3.

### Aparece "Missing or insufficient permissions"

Las reglas bloquean algo que la app necesita. Restaura las reglas anteriores
en la consola y avisa para corregirlas.

### El codigo de invitacion dice "no valido"

1. Comprueba en la consola: **Firestore -> Datos -> invitations**
2. Si no hay documentos, el movil que invito no pudo subir la invitacion
3. Si hay documentos, revisa que el codigo coincide exactamente (8 caracteres,
   sin 0, O, 1 ni I)

### Los errores de sincronizacion no se ven en ningun sitio

Es una limitacion actual conocida: en `FirestoreRepository` los fallos se
capturan y solo se escriben en el registro (Logcat). En una fase posterior se
mostraran en la propia app para que dejen de pasar desapercibidos.
