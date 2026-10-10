package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.MovementType
import com.example.data.model.PaymentMethod
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @org.junit.Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
      val options = com.google.firebase.FirebaseOptions.Builder()
        .setApplicationId("1:423485758520:android:3c25338495f24386501acd")
        .setProjectId("gen-lang-client-0041139520")
        .setApiKey("AIzaSyCn0GplJ7e1r6INUGEZ_Hn5asDuVC7alfI")
        .build()
      com.google.firebase.FirebaseApp.initializeApp(context, options)
    }
  }

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SocioFinanzas", appName)
  }

  @Test
  fun `launch MainActivity test`() {
    val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java)
    controller.setup()
    assertNotNull(controller.get())
  }

  @Test
  fun `database and multi-company segregation test`() = kotlinx.coroutines.test.runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = com.example.data.db.AppDatabase.getDatabase(context)
    val repo = com.example.data.repository.FinanceRepository(db)
    repo.seedInitialDataIfEmpty()

    val groups = repo.allGroups.first()
    assertTrue("Should have multiple seeded companies", groups.size >= 2)

    val g1 = groups.firstOrNull { it.id == "group_cafe_esquina" } ?: groups[0]
    val g2 = groups.firstOrNull { it.id == "group_panaderia_sanjuan" } ?: groups[1]
    assertNotEquals("Group IDs must be different", g1.id, g2.id)

    // Verify products are strictly separated per company
    val g1Products = repo.getProducts(g1.id).first()
    val g2Products = repo.getProducts(g2.id).first()
    assertTrue("Group 1 must have products", g1Products.isNotEmpty())
    assertTrue("Group 2 must have products", g2Products.isNotEmpty())
    assertTrue("Group 1 products must belong to group 1", g1Products.all { it.groupId == g1.id })
    assertTrue("Group 2 products must belong to group 2", g2Products.all { it.groupId == g2.id })

    // Verify movements are strictly separated per company
    val g1Movements = repo.getMovements(g1.id).first()
    val g2Movements = repo.getMovements(g2.id).first()
    assertTrue("Group 1 must have movements", g1Movements.isNotEmpty())
    assertTrue("Group 2 must have movements", g2Movements.isNotEmpty())
    assertTrue("Group 1 movements must belong to group 1", g1Movements.all { it.groupId == g1.id })
    assertTrue("Group 2 movements must belong to group 2", g2Movements.all { it.groupId == g2.id })

    // Verify new company creation
    val createResult = repo.createGroup("Restaurante El Portal", "Nicolás Admin", "nicolas@portal.co")
    assertTrue(createResult.isSuccess)
    val newGroup = createResult.getOrThrow()
    assertEquals("Restaurante El Portal", newGroup.name)

    val updatedGroups = repo.allGroups.first()
    assertTrue(updatedGroups.any { it.id == newGroup.id })

    val newMembers = repo.getMembers(newGroup.id).first()
    assertTrue("New group must have creator as Admin", newMembers.any { it.role == com.example.data.model.Role.ADMIN })

    // Add product to new company
    val addProdResult = repo.addProduct(newGroup.id, "Almuerzo Ejecutivo", "Platos", "comida", 16_000L)
    assertTrue(addProdResult.isSuccess)
    val portalProducts = repo.getProducts(newGroup.id).first()
    assertEquals(1, portalProducts.size)
    assertEquals("Almuerzo Ejecutivo", portalProducts.first().name)
    assertEquals(16_000L, portalProducts.first().standardPriceCop)
  }

  @Test
  fun `room database transaction entity and dao for sales purchases and loans test`() = kotlinx.coroutines.test.runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = com.example.data.db.AppDatabase.getDatabase(context)
    val transactionDao = db.transactionDao()

    val testGroupId = "test_company_trans_${System.currentTimeMillis()}"

    // 1. Insert Venta (Sale)
    val venta = com.example.data.model.Transaction(
        groupId = testGroupId,
        type = com.example.data.model.TransactionType.VENTA,
        amount = 150_000.0,
        description = "Venta de 5 combos especiales",
        paymentMethod = PaymentMethod.EFECTIVO,
        customerOrDebtor = "Cliente Mostrador"
    )
    transactionDao.insertTransaction(venta)

    // 2. Insert Compra (Purchase)
    val compra = com.example.data.model.Transaction(
        groupId = testGroupId,
        type = com.example.data.model.TransactionType.COMPRA,
        amount = 75_000.0,
        description = "Compra de ingredientes e insumos",
        paymentMethod = PaymentMethod.TRANSFERENCIA
    )
    transactionDao.insertTransaction(compra)

    // 3. Insert Préstamo (Loan)
    val prestamo = com.example.data.model.Transaction(
        groupId = testGroupId,
        type = com.example.data.model.TransactionType.PRESTAMO,
        amount = 500_000.0,
        description = "Préstamo de caja a socio para compras urgentes",
        paymentMethod = PaymentMethod.TRANSFERENCIA,
        customerOrDebtor = "Carlos Socio",
        interestRate = 0.0,
        installments = 2
    )
    transactionDao.insertTransaction(prestamo)

    // Verify retrieval by ID
    val retrievedVenta = transactionDao.getTransactionById(venta.id)
    assertNotNull(retrievedVenta)
    assertEquals(150_000.0, retrievedVenta!!.amount, 0.01)
    assertEquals(com.example.data.model.TransactionType.VENTA, retrievedVenta.type)

    // Verify sales query
    val groupSales = transactionDao.getSalesByGroup(testGroupId).first()
    assertEquals(1, groupSales.size)
    assertEquals("Venta de 5 combos especiales", groupSales.first().description)

    // Verify purchases query
    val groupPurchases = transactionDao.getPurchasesByGroup(testGroupId).first()
    assertEquals(1, groupPurchases.size)
    assertEquals(75_000.0, groupPurchases.first().amount, 0.01)

    // Verify loans query
    val groupLoans = transactionDao.getLoansByGroup(testGroupId).first()
    assertEquals(1, groupLoans.size)
    assertEquals("Carlos Socio", groupLoans.first().customerOrDebtor)
    assertEquals(500_000.0, groupLoans.first().amount, 0.01)

    // Verify group segregation and total count
    val allGroupTrans = transactionDao.getTransactionsByGroup(testGroupId).first()
    assertEquals(3, allGroupTrans.size)

    // Verify update
    val updatedVenta = venta.copy(amount = 180_000.0, description = "Venta actualizada")
    transactionDao.updateTransaction(updatedVenta)
    val postUpdate = transactionDao.getTransactionById(venta.id)
    assertEquals(180_000.0, postUpdate!!.amount, 0.01)
    assertEquals("Venta actualizada", postUpdate.description)

    // Verify delete
    transactionDao.deleteTransactionById(compra.id)
    val postDeletePurchases = transactionDao.getPurchasesByGroup(testGroupId).first()
    assertEquals(0, postDeletePurchases.size)

    // Cleanup group transactions
    transactionDao.deleteTransactionsByGroup(testGroupId)
    val finalTrans = transactionDao.getTransactionsByGroup(testGroupId).first()
    assertEquals(0, finalTrans.size)
  }
}
