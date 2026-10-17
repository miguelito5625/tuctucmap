import {
  Component,
  OnInit,
  OnDestroy,
  signal,
  computed,
  inject,
  ChangeDetectionStrategy,
} from '@angular/core';
import { CommonModule, SlicePipe, DecimalPipe } from '@angular/common';
import { Subject, takeUntil } from 'rxjs';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatChipsModule } from '@angular/material/chips';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import {
  MatDialog,
  MatDialogModule,
  MatDialogRef,
  MAT_DIALOG_DATA,
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';

import { SupabaseService } from '../../services/supabase.service';
import {
  Driver,
  DriverStatus,
  SubscriptionPlan,
  PaymentMethod,
  FinancialKPIs
} from '../../core/models/driver.model';
import { DriverHistoryDialogComponent } from './driver-history-dialog.component';

// ── Inline Dialog Component ────────────────────────────────────────────────

export interface SubscriptionDialogData {
  driver: Driver;
}

@Component({
  selector: 'app-subscription-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatDialogModule,
  ],
  template: `
    <div class="sub-dialog">
      <div class="sub-dialog__header">
        <div class="sub-dialog__header-icon">
          <mat-icon>card_membership</mat-icon>
        </div>
        <div>
          <h2 class="sub-dialog__title">Gestionar Suscripción</h2>
          <p class="sub-dialog__subtitle">{{ data.driver.nombre }} ({{ data.driver.placas }})</p>
        </div>
      </div>

      @if (data.driver.estadoAprobacion === 'suscripcion_vencida') {
        <div class="sub-dialog__expired-banner">
          <mat-icon>warning_amber</mat-icon>
          <span>La suscripción de este conductor ha vencido.</span>
        </div>
      }

      <div class="sub-dialog__body">
        <p class="sub-dialog__current-label">Vencimiento registrado actualmente:</p>
        <p class="sub-dialog__current-value">{{ currentExpiryLabel }}</p>

        <!-- Selección de Plan -->
        <mat-form-field appearance="outline" class="sub-dialog__field">
          <mat-label>Plan de Suscripción</mat-label>
          <mat-select [formControl]="planControl" (selectionChange)="onPlanChange($event.value)">
            <mat-option value="semanal">Plan Semanal (7 días) — Q25.00</mat-option>
            <mat-option value="quincenal">Plan Quincenal (15 días) — Q45.00</mat-option>
            <mat-option value="mensual">Plan Mensual (30 días) — Q80.00</mat-option>
            <mat-option value="personalizado">Personalizado (Fecha libre)</mat-option>
          </mat-select>
        </mat-form-field>

        <div class="sub-dialog__row">
          <!-- Monto Cobrado -->
          <mat-form-field appearance="outline" class="sub-dialog__field-half">
            <mat-label>Monto Cobrado (Q)</mat-label>
            <input matInput type="number" [formControl]="amountControl" placeholder="25.00" />
          </mat-form-field>

          <!-- Método de Pago -->
          <mat-form-field appearance="outline" class="sub-dialog__field-half">
            <mat-label>Método de Pago</mat-label>
            <mat-select [formControl]="paymentMethodControl">
              <mat-option value="deposito_bancario">Depósito Banrural</mat-option>
              <mat-option value="transferencia">Transferencia Bancaria</mat-option>
              <mat-option value="efectivo">Efectivo en Mano</mat-option>
              <mat-option value="gratis_prueba">Cortesía / Prueba</mat-option>
            </mat-select>
          </mat-form-field>
        </div>

        <!-- No. de Boleta o Comprobante -->
        <mat-form-field appearance="outline" class="sub-dialog__field">
          <mat-label>No. de Boleta / Referencia Bancaria</mat-label>
          <input matInput [formControl]="referenceControl" placeholder="Ej. 98451203" />
        </mat-form-field>

        <!-- Nueva fecha de vencimiento -->
        <mat-form-field appearance="outline" class="sub-dialog__field">
          <mat-label>Fecha de vencimiento resultante</mat-label>
          <input
            matInput
            [matDatepicker]="picker"
            [formControl]="dateControl"
            [min]="today"
            placeholder="dd/mm/aaaa"
          />
          <mat-datepicker-toggle matIconSuffix [for]="picker" />
          <mat-datepicker #picker />
          @if (dateControl.hasError('required')) {
            <mat-error>Selecciona una fecha válida</mat-error>
          }
        </mat-form-field>
      </div>

      <div class="sub-dialog__actions">
        <button mat-button (click)="onCancel()">Cancelar</button>
        <button
          mat-flat-button
          id="set-expiry-btn-{{ data.driver.uid }}"
          class="sub-dialog__save-btn"
          [disabled]="dateControl.invalid"
          (click)="onSave()"
        >
          <mat-icon>check_circle</mat-icon>
          Activar y Guardar Cuota
        </button>
      </div>
    </div>
  `,
  styles: [`
    .sub-dialog {
      background: #1e293b;
      color: #f1f5f9;
      border-radius: 20px;
      overflow: hidden;
      width: 100%;
      max-width: 500px;
      min-width: 0;
      box-sizing: border-box;
    }

    .sub-dialog__header {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 22px 24px 18px;
      border-bottom: 1px solid rgba(255, 255, 255, 0.07);
    }

    .sub-dialog__header-icon {
      width: 46px;
      height: 46px;
      border-radius: 12px;
      background: rgba(99, 102, 241, 0.15);
      border: 1px solid rgba(99, 102, 241, 0.3);
      display: flex;
      align-items: center;
      justify-content: center;
      color: #818cf8;

      mat-icon {
        font-size: 26px;
        width: 26px;
        height: 26px;
      }
    }

    .sub-dialog__title {
      margin: 0;
      font-size: 1.15rem;
      font-weight: 700;
      color: #f8fafc;
    }

    .sub-dialog__subtitle {
      margin: 2px 0 0;
      font-size: 0.85rem;
      color: #94a3b8;
    }

    .sub-dialog__expired-banner {
      display: flex;
      align-items: center;
      gap: 10px;
      margin: 14px 24px 0;
      padding: 10px 14px;
      background: rgba(251, 146, 60, 0.12);
      border: 1px solid rgba(251, 146, 60, 0.25);
      border-radius: 10px;
      font-size: 0.85rem;
      color: #fb923c;

      mat-icon {
        font-size: 20px;
        width: 20px;
        height: 20px;
      }
    }

    .sub-dialog__body {
      padding: 18px 24px 8px;
    }

    .sub-dialog__current-label {
      margin: 0 0 2px;
      font-size: 0.72rem;
      color: #64748b;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      font-weight: 600;
    }

    .sub-dialog__current-value {
      margin: 0 0 16px;
      font-size: 0.95rem;
      color: #f9d423;
      font-weight: 600;
    }

    .sub-dialog__row {
      display: flex;
      gap: 12px;
    }

    .sub-dialog__field-half {
      flex: 1;
    }

    .sub-dialog__field {
      width: 100%;
      margin-bottom: 4px;
    }

    ::ng-deep {
      .sub-dialog {
        .mat-mdc-form-field-flex {
          background: rgba(255, 255, 255, 0.04);
        }
        .mdc-notched-outline__leading,
        .mdc-notched-outline__notch,
        .mdc-notched-outline__trailing {
          border-color: rgba(255, 255, 255, 0.15) !important;
        }
        .mat-mdc-input-element, .mat-mdc-select-value {
          color: #f1f5f9 !important;
        }
        .mat-mdc-floating-label {
          color: #94a3b8 !important;
        }
        &.mat-focused {
          .mdc-notched-outline__leading,
          .mdc-notched-outline__notch,
          .mdc-notched-outline__trailing {
            border-color: #60a5fa !important;
          }
        }
      }
    }

    .sub-dialog__actions {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      padding: 12px 24px 20px;
      gap: 12px;
      border-top: 1px solid rgba(255, 255, 255, 0.06);
    }

    .sub-dialog__save-btn {
      background: linear-gradient(135deg, #22c55e, #16a34a) !important;
      color: #fff !important;
      border-radius: 10px !important;
      font-weight: 600 !important;
      font-size: 0.85rem !important;

      mat-icon {
        font-size: 18px;
        width: 18px;
        height: 18px;
        margin-right: 4px;
      }
    }

    @media (max-width: 480px) {
      .sub-dialog__row {
        flex-direction: column;
        gap: 0;
      }
      .sub-dialog__plans {
        grid-template-columns: 1fr;
      }
      .sub-dialog__header {
        padding: 16px 18px 14px;
      }
      .sub-dialog__body {
        padding: 16px 18px 8px;
      }
      .sub-dialog__actions {
        padding: 12px 18px 16px;
        flex-direction: column-reverse;
        button {
          width: 100%;
        }
      }
    }
  `],
})
export class SubscriptionDialogComponent implements OnInit {
  readonly data: SubscriptionDialogData = inject(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<SubscriptionDialogComponent>);

  readonly today = new Date();
  readonly planControl = new FormControl<SubscriptionPlan>('semanal', { nonNullable: true });
  readonly amountControl = new FormControl<number>(25.00, { nonNullable: true });
  readonly paymentMethodControl = new FormControl<PaymentMethod>('deposito_bancario', { nonNullable: true });
  readonly referenceControl = new FormControl<string>('', { nonNullable: true });
  readonly dateControl = new FormControl<Date | null>(null, Validators.required);

  ngOnInit(): void {
    // Inicializar con plan semanal (7 días por defecto)
    this.calculatePlanExpiry('semanal');
  }

  onPlanChange(plan: string): void {
    this.calculatePlanExpiry(plan as SubscriptionPlan);
  }

  private calculatePlanExpiry(plan: SubscriptionPlan): void {
    const currentExpiry = this.data.driver.fechaVencimientoSuscripcion || 0;
    // Si el conductor paga puntualmente antes de vencer, sumar los días a su vencimiento actual
    const baseDate = currentExpiry > Date.now() ? new Date(currentExpiry) : new Date();
    switch (plan) {
      case 'semanal':
        baseDate.setDate(baseDate.getDate() + 7);
        this.amountControl.setValue(25.00);
        break;
      case 'quincenal':
        baseDate.setDate(baseDate.getDate() + 15);
        this.amountControl.setValue(45.00);
        break;
      case 'mensual':
        baseDate.setDate(baseDate.getDate() + 30);
        this.amountControl.setValue(80.00);
        break;
      case 'personalizado':
        return; // Mantiene la fecha seleccionada
    }
    this.dateControl.setValue(baseDate);
  }

  get currentExpiryLabel(): string {
    const ts = this.data.driver.fechaVencimientoSuscripcion;
    if (!ts || ts === 0) return 'Sin fecha asignada';
    return new Date(ts).toLocaleDateString('es-GT', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  onCancel(): void {
    this.dialogRef.close();
  }

  onSave(): void {
    if (this.dateControl.invalid || !this.dateControl.value) return;
    const date = new Date(this.dateControl.value);
    date.setHours(23, 59, 59, 999);
    this.dialogRef.close({
      action: 'save',
      timestamp: date.getTime(),
      planType: this.planControl.value,
      amountPaid: this.amountControl.value || 0,
      paymentMethod: this.paymentMethodControl.value,
      referenceNumber: this.referenceControl.value || ''
    });
  }
}

// ── Main Drivers Component ──────────────────────────────────────────────────

@Component({
  selector: 'app-drivers',
  standalone: true,
  imports: [
    CommonModule,
    SlicePipe,
    DecimalPipe,
    ReactiveFormsModule,
    MatTableModule,
    MatTabsModule,
    MatChipsModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatTooltipModule,
    MatDialogModule,
  ],
  templateUrl: './drivers.component.html',
  styleUrl: './drivers.component.scss',
})
export class DriversComponent implements OnInit, OnDestroy {
  private readonly supabaseService = inject(SupabaseService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);
  private readonly destroy$ = new Subject<void>();

  // Data signals
  readonly drivers = signal<Driver[]>([]);
  readonly isLoading = signal(true);
  readonly selectedTabIndex = signal(0);
  readonly processingUid = signal<string | null>(null);
  private banruralAccount = '3402-0987-65';
  private weeklyPrice = 25.00;

  // Financial KPIs
  readonly financialKPIs = signal<FinancialKPIs>({
    totalRevenue: 0,
    monthRevenue: 0,
    expiringIn7Days: 0,
    expiringAmountExpected: 0,
    activeFleetCount: 0
  });

  // Table columns
  readonly displayedColumns: string[] = [
    'nombre',
    'email',
    'telefono',
    'placas',
    'estadoAprobacion',
    'fechaVencimiento',
    'acciones',
  ];

  // Tab mapping
  private readonly tabStatusMap: Record<number, DriverStatus | null> = {
    0: null, // Todos
    1: 'pendiente',
    2: 'aprobado',
    3: 'rechazado',
    4: 'suscripcion_vencida',
  };

  // Filtered drivers based on selected tab
  readonly filteredDrivers = computed(() => {
    const tab = this.selectedTabIndex();
    const status = this.tabStatusMap[tab];
    const list = this.drivers();

    if (!status) return list;
    return list.filter((d) => d.estadoAprobacion === status);
  });

  // Stats
  readonly totalCount = computed(() => this.drivers().length);
  readonly pendingCount = computed(() => this.countByStatus('pendiente'));
  readonly approvedCount = computed(() => this.countByStatus('aprobado'));
  readonly rejectedCount = computed(() => this.countByStatus('rechazado'));
  readonly expiredCount = computed(() => this.countByStatus('suscripcion_vencida'));

  private countByStatus(status: DriverStatus): number {
    return this.drivers().filter((d) => d.estadoAprobacion === status).length;
  }

  ngOnInit(): void {
    this.subscribeDrivers();
    this.loadFinancialStats();
    this.supabaseService.getAppConfig().then((cfg) => {
      this.banruralAccount = cfg.banrural_account;
      this.weeklyPrice = cfg.price_semanal;
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private subscribeDrivers(): void {
    this.supabaseService
      .getDrivers()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (list) => {
          this.drivers.set(list);
          this.isLoading.set(false);
          this.loadFinancialStats();
        },
        error: () => {
          this.isLoading.set(false);
          this.showSnack('Error al cargar la lista de conductores', 'snack-error');
        },
      });
  }

  async loadFinancialStats(): Promise<void> {
    try {
      const stats = await this.supabaseService.getFinancialStats();
      this.financialKPIs.set(stats);
    } catch {
      // Ignorar fallo no crítico de estadísticas
    }
  }

  onTabChange(index: number): void {
    this.selectedTabIndex.set(index);
  }

  // ── Actions ────────────────────────────────────────────────
  async approveDriver(driver: Driver): Promise<void> {
    this.processingUid.set(driver.uid);
    try {
      await this.supabaseService.approveDriver(driver.uid);
      this.showSnack(`✓ Conductor ${driver.nombre} aprobado`, 'snack-success');
    } catch {
      this.showSnack(`Error al aprobar a ${driver.nombre}`, 'snack-error');
    } finally {
      this.processingUid.set(null);
    }
  }

  async rejectDriver(driver: Driver): Promise<void> {
    this.processingUid.set(driver.uid);
    try {
      await this.supabaseService.rejectDriver(driver.uid);
      this.showSnack(`Conductor ${driver.nombre} rechazado`, 'snack-warn');
    } catch {
      this.showSnack(`Error al rechazar a ${driver.nombre}`, 'snack-error');
    } finally {
      this.processingUid.set(null);
    }
  }

  async deleteDriver(driver: Driver): Promise<void> {
    const confirm = window.confirm(
      `¿Estás seguro de eliminar permanentemente a ${driver.nombre}? Esta acción no se puede deshacer.`
    );
    if (!confirm) return;

    this.processingUid.set(driver.uid);
    try {
      await this.supabaseService.deleteDriver(driver.uid);
      this.showSnack(`Conductor ${driver.nombre} eliminado`, 'snack-warn');
    } catch {
      this.showSnack(`Error al eliminar a ${driver.nombre}`, 'snack-error');
    } finally {
      this.processingUid.set(null);
    }
  }

  openSubscriptionDialog(driver: Driver): void {
    const dialogRef = this.dialog.open(SubscriptionDialogComponent, {
      data: { driver } satisfies SubscriptionDialogData,
      panelClass: 'dark-dialog-panel',
      disableClose: false,
    });

    dialogRef.afterClosed().subscribe(async (result) => {
      if (!result) return;

      this.processingUid.set(driver.uid);
      try {
        await this.supabaseService.setSubscriptionExpiry(
          driver.uid,
          result.timestamp as number,
          result.planType,
          result.amountPaid,
          result.paymentMethod,
          result.referenceNumber
        );
        this.showSnack(`📅 Cuota de ${driver.nombre} activada exitosamente (Q${result.amountPaid})`, 'snack-success');
        this.loadFinancialStats();
      } catch {
        this.showSnack('Error al registrar la suscripción', 'snack-error');
      } finally {
        this.processingUid.set(null);
      }
    });
  }

  /**
   * Envía un recordatorio de cobro personalizado a través de WhatsApp Web en 1 clic.
   */
  sendWhatsAppPaymentReminder(driver: Driver): void {
    let phone = driver.telefono.replace(/\s+/g, '').replace(/-/g, '');
    if (phone.length === 8) phone = '502' + phone;

    const expiryStr = this.formatExpiryDate(driver.fechaVencimientoSuscripcion);
    const msg = `Hola ${driver.nombre}, te saludamos de TucTucMap. Te recordamos que tu cuota para el Torito placas ${driver.placas} vence el ${expiryStr}. Para mantenerte visible en el mapa y recibiendo viajes, por favor deposita tu cuota de Q${this.weeklyPrice.toFixed(2)} al número de cuenta Banrural ${this.banruralAccount} y envíanos tu boleta. ¡Muchas gracias!`;

    const url = `https://api.whatsapp.com/send?phone=${phone}&text=${encodeURIComponent(msg)}`;
    window.open(url, '_blank');
  }

  viewHistory(driver: Driver): void {
    this.dialog.open(DriverHistoryDialogComponent, {
      data: { driverId: driver.uid, driverName: driver.nombre },
      width: '650px',
      panelClass: 'dark-dialog'
    });
  }

  // ── Helpers ────────────────────────────────────────────────
  getStatusIcon(status: DriverStatus): string {
    switch (status) {
      case 'aprobado':
        return 'check_circle';
      case 'rechazado':
        return 'cancel';
      case 'suscripcion_vencida':
        return 'card_membership';
      default:
        return 'schedule';
    }
  }

  getStatusLabel(status: DriverStatus): string {
    switch (status) {
      case 'aprobado':
        return 'Aprobado';
      case 'rechazado':
        return 'Rechazado';
      case 'suscripcion_vencida':
        return 'Vencida';
      default:
        return 'Pendiente';
    }
  }

  getExpiryStatus(driver: Driver): 'expired' | 'warning' | 'ok' | 'none' {
    const ts = driver.fechaVencimientoSuscripcion;
    if (!ts || ts === 0) return 'none';
    const now = Date.now();
    if (ts < now) return 'expired';
    const sevenDays = 7 * 24 * 60 * 60 * 1000;
    if (ts - now <= sevenDays) return 'warning';
    return 'ok';
  }

  getExpiryIcon(driver: Driver): string {
    const status = this.getExpiryStatus(driver);
    switch (status) {
      case 'expired':
        return 'warning_amber';
      case 'warning':
        return 'schedule';
      case 'ok':
        return 'event_available';
      default:
        return '';
    }
  }

  formatExpiryDate(timestamp: number | undefined): string {
    if (!timestamp || timestamp === 0) return 'Sin fecha';
    return new Date(timestamp).toLocaleDateString('es-GT', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    });
  }

  getInitials(name: string): string {
    return name
      .split(' ')
      .slice(0, 2)
      .map((w) => w.charAt(0).toUpperCase())
      .join('');
  }

  isProcessing(uid: string): boolean {
    return this.processingUid() === uid;
  }

  exportDriversCsv(): void {
    const list = this.drivers();
    if (!list || list.length === 0) return;

    const headers = ['UID', 'Nombre', 'Correo', 'Teléfono', 'Placas', 'Estado Aprobación', 'Vencimiento Suscripción'];
    const rows = list.map((d) => [
      `"${d.uid || ''}"`,
      `"${(d.nombre || '').replace(/"/g, '""')}"`,
      `"${(d.email || '').replace(/"/g, '""')}"`,
      `"${d.telefono || ''}"`,
      `"${d.placas || ''}"`,
      `"${d.estadoAprobacion || ''}"`,
      `"${this.formatExpiryDate(d.fechaVencimientoSuscripcion)}"`,
    ]);

    const csvContent = '\uFEFF' + [headers.join(','), ...rows.map((r) => r.join(','))].join('\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.setAttribute('href', url);
    link.setAttribute('download', `conductores_flota_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }

  private showSnack(message: string, panelClass: string): void {
    this.snackBar.open(message, 'OK', {
      duration: 3500,
      panelClass: [panelClass],
      horizontalPosition: 'right',
      verticalPosition: 'bottom',
    });
  }
}
