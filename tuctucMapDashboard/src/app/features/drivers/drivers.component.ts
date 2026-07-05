import {
  Component,
  OnInit,
  OnDestroy,
  signal,
  computed,
  inject,
  ChangeDetectionStrategy,
} from '@angular/core';
import { SlicePipe } from '@angular/common';
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
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';

import { SupabaseService } from '../../services/supabase.service';
import { Driver, DriverStatus } from '../../core/models/driver.model';
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
    ReactiveFormsModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
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
          <p class="sub-dialog__subtitle">{{ data.driver.nombre }}</p>
        </div>
      </div>

      @if (data.driver.estadoAprobacion === 'suscripcion_vencida') {
        <div class="sub-dialog__expired-banner">
          <mat-icon>warning_amber</mat-icon>
          <span>La suscripción de este conductor ha vencido.</span>
        </div>
      }

      <div class="sub-dialog__body">
        <p class="sub-dialog__current-label">Fecha de vencimiento actual:</p>
        <p class="sub-dialog__current-value">{{ currentExpiryLabel }}</p>

        <mat-form-field appearance="outline" class="sub-dialog__field">
          <mat-label>Nueva fecha de vencimiento</mat-label>
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
            <mat-error>Selecciona una fecha</mat-error>
          }
        </mat-form-field>
      </div>

      <div class="sub-dialog__actions">
        @if (data.driver.estadoAprobacion === 'suscripcion_vencida') {
          <button
            mat-flat-button
            id="renew-btn-{{ data.driver.uid }}"
            class="sub-dialog__renew-btn"
            (click)="onRenew()"
          >
            <mat-icon>refresh</mat-icon>
            Renovar Suscripción
          </button>
        }
        <div class="sub-dialog__actions-right">
          <button mat-button (click)="onCancel()">Cancelar</button>
          <button
            mat-flat-button
            id="set-expiry-btn-{{ data.driver.uid }}"
            class="sub-dialog__save-btn"
            [disabled]="dateControl.invalid"
            (click)="onSave()"
          >
            <mat-icon>save</mat-icon>
            Guardar Fecha
          </button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .sub-dialog {
      background: #1e293b;
      color: #f1f5f9;
      border-radius: 20px;
      overflow: hidden;
      min-width: 380px;
      max-width: 460px;
    }

    .sub-dialog__header {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 24px 24px 20px;
      border-bottom: 1px solid rgba(255, 255, 255, 0.07);
    }

    .sub-dialog__header-icon {
      width: 48px;
      height: 48px;
      border-radius: 14px;
      background: rgba(251, 146, 60, 0.15);
      border: 1px solid rgba(251, 146, 60, 0.3);
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;

      mat-icon {
        color: #fb923c;
        font-size: 24px;
        width: 24px;
        height: 24px;
      }
    }

    .sub-dialog__title {
      margin: 0;
      font-size: 1.1rem;
      font-weight: 700;
      color: #f1f5f9;
    }

    .sub-dialog__subtitle {
      margin: 4px 0 0;
      font-size: 0.85rem;
      color: #64748b;
    }

    .sub-dialog__expired-banner {
      display: flex;
      align-items: center;
      gap: 10px;
      margin: 16px 24px 0;
      padding: 12px 16px;
      background: rgba(251, 146, 60, 0.1);
      border: 1px solid rgba(251, 146, 60, 0.25);
      border-radius: 12px;
      font-size: 0.85rem;
      color: #fb923c;

      mat-icon {
        font-size: 20px;
        width: 20px;
        height: 20px;
        flex-shrink: 0;
      }
    }

    .sub-dialog__body {
      padding: 20px 24px 8px;
    }

    .sub-dialog__current-label {
      margin: 0 0 4px;
      font-size: 0.72rem;
      color: #64748b;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      font-weight: 600;
    }

    .sub-dialog__current-value {
      margin: 0 0 20px;
      font-size: 0.95rem;
      color: #94a3b8;
      font-weight: 500;
    }

    .sub-dialog__field {
      width: 100%;

      ::ng-deep {
        .mat-mdc-form-field-flex {
          background: rgba(255, 255, 255, 0.04);
        }
        .mdc-notched-outline__leading,
        .mdc-notched-outline__notch,
        .mdc-notched-outline__trailing {
          border-color: rgba(255, 255, 255, 0.15) !important;
        }
        .mat-mdc-form-field-focus-overlay {
          background: transparent;
        }
        .mat-mdc-input-element {
          color: #f1f5f9 !important;
          caret-color: #60a5fa;
        }
        .mat-mdc-floating-label {
          color: #64748b !important;
        }
        &.mat-focused {
          .mdc-notched-outline__leading,
          .mdc-notched-outline__notch,
          .mdc-notched-outline__trailing {
            border-color: #60a5fa !important;
          }
          .mat-mdc-floating-label {
            color: #60a5fa !important;
          }
        }
        .mat-datepicker-toggle {
          color: #64748b;
        }
      }
    }

    .sub-dialog__actions {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 12px 24px 24px;
      gap: 12px;
      border-top: 1px solid rgba(255, 255, 255, 0.06);
      flex-wrap: wrap;
    }

    .sub-dialog__actions-right {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-left: auto;
    }

    .sub-dialog__renew-btn {
      background: rgba(52, 211, 153, 0.15) !important;
      color: #34d399 !important;
      border: 1px solid rgba(52, 211, 153, 0.3);
      border-radius: 10px !important;
      font-weight: 600 !important;
      font-size: 0.8rem !important;

      mat-icon {
        font-size: 18px;
        width: 18px;
        height: 18px;
        margin-right: 4px;
      }

      &:hover {
        background: rgba(52, 211, 153, 0.25) !important;
      }
    }

    .sub-dialog__save-btn {
      background: linear-gradient(135deg, #3b82f6, #6366f1) !important;
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

      &:disabled {
        opacity: 0.4 !important;
      }

      &:not(:disabled):hover {
        filter: brightness(1.1);
      }
    }

    ::ng-deep .mat-calendar {
      background: #1e293b !important;
      color: #f1f5f9 !important;
    }

    ::ng-deep .mat-calendar-body-cell-content {
      color: #f1f5f9 !important;
    }

    ::ng-deep .mat-calendar-body-selected {
      background: #3b82f6 !important;
      color: #fff !important;
    }

    ::ng-deep .mat-calendar-body-today:not(.mat-calendar-body-selected) {
      border-color: #60a5fa !important;
    }
  `],
})
export class SubscriptionDialogComponent {
  readonly data: SubscriptionDialogData = inject(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<SubscriptionDialogComponent>);

  readonly today = new Date();
  readonly dateControl = new FormControl<Date | null>(null, Validators.required);

  get currentExpiryLabel(): string {
    const ts = this.data.driver.fechaVencimientoSuscripcion;
    if (!ts || ts === 0) return 'Sin fecha asignada';
    return new Date(ts).toLocaleDateString('es-GT', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    });
  }

  onCancel(): void {
    this.dialogRef.close();
  }

  onSave(): void {
    if (this.dateControl.invalid || !this.dateControl.value) return;
    const date = new Date(this.dateControl.value);
    date.setHours(23, 59, 59, 999);
    this.dialogRef.close({ action: 'save', timestamp: date.getTime() });
  }

  onRenew(): void {
    this.dialogRef.close({ action: 'renew' });
  }
}

// ── Main Drivers Component ──────────────────────────────────────────────────

@Component({
  selector: 'app-drivers',
  standalone: true,
  imports: [
    SlicePipe,
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
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatNativeDateModule,
  ],
  templateUrl: './drivers.component.html',
  styleUrl: './drivers.component.scss',
})
export class DriversComponent implements OnInit, OnDestroy {
  private readonly SupabaseService = inject(SupabaseService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);
  private readonly destroy$ = new Subject<void>();

  // ── State signals ──────────────────────────────────────────
  readonly allDrivers = signal<Driver[]>([]);
  readonly isLoading = signal(true);
  readonly selectedTabIndex = signal(0);
  readonly processingUid = signal<string | null>(null);

  readonly displayedColumns: string[] = [
    'nombre',
    'email',
    'telefono',
    'placas',
    'estadoAprobacion',
    'fechaVencimiento',
    'acciones',
  ];

  // ── Derived / computed ─────────────────────────────────────
  readonly filteredDrivers = computed<Driver[]>(() => {
    const all = this.allDrivers();
    switch (this.selectedTabIndex()) {
      case 1:
        return all.filter((d) => d.estadoAprobacion === 'pendiente');
      case 2:
        return all.filter((d) => d.estadoAprobacion === 'aprobado');
      case 3:
        return all.filter((d) => d.estadoAprobacion === 'rechazado');
      case 4:
        return all.filter((d) => d.estadoAprobacion === 'suscripcion_vencida');
      default:
        return all;
    }
  });

  readonly totalCount = computed(() => this.allDrivers().length);
  readonly pendingCount = computed(
    () => this.allDrivers().filter((d) => d.estadoAprobacion === 'pendiente').length,
  );
  readonly approvedCount = computed(
    () => this.allDrivers().filter((d) => d.estadoAprobacion === 'aprobado').length,
  );
  readonly rejectedCount = computed(
    () => this.allDrivers().filter((d) => d.estadoAprobacion === 'rechazado').length,
  );
  readonly expiredCount = computed(
    () => this.allDrivers().filter((d) => d.estadoAprobacion === 'suscripcion_vencida').length,
  );

  // ── Lifecycle ──────────────────────────────────────────────
  ngOnInit(): void {
    this.SupabaseService
      .getDrivers()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (drivers) => {
          this.allDrivers.set(drivers);
          this.isLoading.set(false);
        },
        error: () => {
          this.isLoading.set(false);
          this.showSnack('Error al conectar con Firebase', 'snack-error');
        },
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ── Handlers ───────────────────────────────────────────────
  onTabChange(index: number): void {
    this.selectedTabIndex.set(index);
  }

  async setSubscriptionExpiry(driver: Driver, timestamp: number): Promise<void> {
    if (this.processingUid()) return;
    this.processingUid.set(driver.uid);
    try {
      await this.SupabaseService.setSubscriptionExpiry(driver.uid, timestamp);
      this.showSnack(`✓ Fecha personalizada para ${driver.nombre} guardada`, 'snack-success');
    } catch {
      this.showSnack('Error al guardar la fecha personalizada', 'snack-error');
    } finally {
      this.processingUid.set(null);
    }
  }

  viewHistory(driver: Driver): void {
    this.dialog.open(DriverHistoryDialogComponent, {
      width: '600px',
      data: { driverId: driver.uid, driverName: driver.nombre },
      panelClass: 'dark-dialog'
    });
  }

  async approveDriver(driver: Driver): Promise<void> {
    if (this.processingUid()) return;
    this.processingUid.set(driver.uid);
    try {
      await this.SupabaseService.approveDriver(driver.uid);
      this.showSnack(`✓ ${driver.nombre} ha sido aprobado`, 'snack-success');
    } catch {
      this.showSnack('Error al aprobar el conductor', 'snack-error');
    } finally {
      this.processingUid.set(null);
    }
  }

  async rejectDriver(driver: Driver): Promise<void> {
    if (this.processingUid()) return;
    this.processingUid.set(driver.uid);
    try {
      await this.SupabaseService.rejectDriver(driver.uid);
      if (driver.estadoAprobacion === 'aprobado') {
        this.showSnack(`✗ La aprobación de ${driver.nombre} ha sido revocada`, 'snack-warn');
      } else {
        this.showSnack(`✗ ${driver.nombre} ha sido rechazado`, 'snack-warn');
      }
    } catch {
      this.showSnack('Error al rechazar el conductor', 'snack-error');
    } finally {
      this.processingUid.set(null);
    }
  }

  async deleteDriver(driver: Driver): Promise<void> {
    if (
      !confirm(
        `¿Eliminar permanentemente a ${driver.nombre}? Esta acción no se puede deshacer.`,
      )
    )
      return;
    if (this.processingUid()) return;
    this.processingUid.set(driver.uid);
    try {
      await this.SupabaseService.deleteDriver(driver.uid);
      this.showSnack(`${driver.nombre} ha sido eliminado`, 'snack-error');
    } catch {
      this.showSnack('Error al eliminar el conductor', 'snack-error');
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

      if (result.action === 'save') {
        this.processingUid.set(driver.uid);
        try {
          await this.SupabaseService.setSubscriptionExpiry(driver.uid, result.timestamp as number);
          this.showSnack(`📅 Fecha de vencimiento actualizada para ${driver.nombre}`, 'snack-success');
        } catch {
          this.showSnack('Error al actualizar la fecha de vencimiento', 'snack-error');
        } finally {
          this.processingUid.set(null);
        }
      } else if (result.action === 'renew') {
        this.processingUid.set(driver.uid);
        try {
          await this.SupabaseService.renewSubscription(driver.uid);
          this.showSnack(`✓ Suscripción de ${driver.nombre} renovada`, 'snack-success');
        } catch {
          this.showSnack('Error al renovar la suscripción', 'snack-error');
        } finally {
          this.processingUid.set(null);
        }
      }
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

  /**
   * Returns the CSS modifier class for the expiry date cell.
   */
  getExpiryStatus(driver: Driver): 'expired' | 'warning' | 'ok' | 'none' {
    const ts = driver.fechaVencimientoSuscripcion;
    if (!ts || ts === 0) return 'none';
    const now = Date.now();
    if (ts < now) return 'expired';
    const sevenDays = 7 * 24 * 60 * 60 * 1000;
    if (ts - now <= sevenDays) return 'warning';
    return 'ok';
  }

  /**
   * Returns the Material icon name for the expiry cell.
   */
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

  /**
   * Formats the expiry timestamp as a localized date string (es-GT).
   */
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

  private showSnack(message: string, panelClass: string): void {
    this.snackBar.open(message, 'OK', {
      duration: 3500,
      panelClass: [panelClass],
      horizontalPosition: 'right',
      verticalPosition: 'bottom',
    });
  }
}
