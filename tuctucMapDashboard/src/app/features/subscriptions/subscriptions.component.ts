import { Component, OnInit, OnDestroy, inject, signal, computed } from '@angular/core';
import { CommonModule, DecimalPipe, DatePipe } from '@angular/common';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subscription } from 'rxjs';
import { MatTableModule } from '@angular/material/table';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatButtonModule } from '@angular/material/button';
import { MatTabsModule } from '@angular/material/tabs';
import { MatChipsModule } from '@angular/material/chips';
import { MatBadgeModule } from '@angular/material/badge';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import {
  MatDialog,
  MatDialogModule,
  MatDialogRef,
  MAT_DIALOG_DATA
} from '@angular/material/dialog';

import { SupabaseService } from '../../services/supabase.service';
import {
  SubscriptionHistory,
  SubscriptionPaymentRequest,
  SubscriptionPlan,
  PaymentMethod
} from '../../core/models/driver.model';

// ── Diálogo: Confirmar Aprobación de Solicitud de Renovación ──────────────────
@Component({
  selector: 'app-approve-renewal-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatIconModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatNativeDateModule
  ],
  template: `
    <div class="confirm-dialog">
      <div class="confirm-dialog__header">
        <div class="confirm-dialog__icon confirm-dialog__icon--approve">
          <mat-icon>check_circle</mat-icon>
        </div>
        <div>
          <h2 class="confirm-dialog__title">Aprobar Renovación</h2>
          <p class="confirm-dialog__subtitle">{{ data.request.driverName }} ({{ data.request.driverPlate }})</p>
        </div>
      </div>

      <div class="confirm-dialog__body">
        <div class="info-card">
          <div class="info-row">
            <span class="label">Plan Solicitado:</span>
            <span class="value value-plan">{{ (data.request.plan_type || 'semanal') | uppercase }}</span>
          </div>
          <div class="info-row">
            <span class="label">Monto a Registrar:</span>
            <span class="value value-amount">Q {{ (data.request.amount || 0) | number:'1.2-2' }}</span>
          </div>
          <div class="info-row">
            <span class="label">No. de Boleta / Ref:</span>
            <span class="value">{{ data.request.reference_number || 'Sin número registrado' }}</span>
          </div>
          <div class="info-row">
            <span class="label">Método de Pago:</span>
            <span class="value">{{ formatPaymentMethod(data.request.payment_method) }}</span>
          </div>
        </div>

        <div class="calculation-notice">
          <mat-icon>auto_awesome</mat-icon>
          <div class="notice-text">
            <strong>Renovación acumulativa automática:</strong>
            <span>Se sumarán {{ daysForPlan }} días a la vigencia actual del conductor. También puedes ajustar la fecha manualmente si es necesario:</span>
          </div>
        </div>

        <mat-form-field appearance="outline" class="full-field">
          <mat-label>Fecha de Vencimiento Resultante</mat-label>
          <input matInput [matDatepicker]="picker" [formControl]="customEndDateControl" [min]="today" />
          <mat-datepicker-toggle matIconSuffix [for]="picker"></mat-datepicker-toggle>
          <mat-datepicker #picker></mat-datepicker>
        </mat-form-field>
      </div>

      <div class="confirm-dialog__actions">
        <button mat-button (click)="dialogRef.close(null)">Cancelar</button>
        <button mat-flat-button class="btn-approve" (click)="onConfirm()">
          <mat-icon>verified</mat-icon>
          Confirmar y Activar
        </button>
      </div>
    </div>
  `,
  styles: [`
    .confirm-dialog {
      background: #1e293b;
      color: #f1f5f9;
      border-radius: 20px;
      padding: 0;
      overflow: hidden;
      max-width: 480px;
      width: 100%;
    }
    .confirm-dialog__header {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 22px 24px;
      border-bottom: 1px solid rgba(255, 255, 255, 0.08);
    }
    .confirm-dialog__icon {
      width: 46px;
      height: 46px;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      mat-icon { font-size: 26px; width: 26px; height: 26px; }
    }
    .confirm-dialog__icon--approve {
      background: rgba(34, 197, 94, 0.15);
      color: #4ade80;
      border: 1px solid rgba(34, 197, 94, 0.3);
    }
    .confirm-dialog__title { margin: 0; font-size: 1.2rem; font-weight: 700; color: #f8fafc; }
    .confirm-dialog__subtitle { margin: 2px 0 0; font-size: 0.85rem; color: #94a3b8; }
    .confirm-dialog__body { padding: 20px 24px 8px; }
    .info-card {
      background: rgba(15, 23, 42, 0.6);
      border-radius: 12px;
      border: 1px solid rgba(255, 255, 255, 0.06);
      padding: 14px 16px;
      margin-bottom: 16px;
    }
    .info-row {
      display: flex;
      justify-content: space-between;
      margin-bottom: 8px;
      font-size: 0.88rem;
      &:last-child { margin-bottom: 0; }
      .label { color: #94a3b8; }
      .value { color: #f1f5f9; font-weight: 500; }
      .value-plan { color: #818cf8; font-weight: 700; }
      .value-amount { color: #4ade80; font-weight: 700; }
    }
    .calculation-notice {
      display: flex;
      gap: 12px;
      padding: 12px 14px;
      background: rgba(99, 102, 241, 0.1);
      border: 1px solid rgba(99, 102, 241, 0.25);
      border-radius: 10px;
      font-size: 0.82rem;
      color: #c7d2fe;
      margin-bottom: 16px;
      mat-icon { font-size: 20px; width: 20px; height: 20px; color: #818cf8; flex-shrink: 0; }
      .notice-text { display: flex; flex-direction: column; gap: 2px; }
    }
    .full-field { width: 100%; margin-top: 4px; }
    .confirm-dialog__actions {
      display: flex;
      justify-content: flex-end;
      gap: 12px;
      padding: 16px 24px 20px;
      border-top: 1px solid rgba(255, 255, 255, 0.08);
      .btn-approve {
        background: linear-gradient(135deg, #16a34a, #22c55e);
        color: #ffffff;
        font-weight: 600;
        border-radius: 10px;
      }
    }
  `]
})
export class ApproveRenewalDialogComponent implements OnInit {
  readonly data: { request: SubscriptionPaymentRequest } = inject(MAT_DIALOG_DATA);
  readonly dialogRef = inject(MatDialogRef<ApproveRenewalDialogComponent>);
  readonly today = new Date();
  readonly customEndDateControl = new FormControl<Date | null>(null, Validators.required);

  get daysForPlan(): number {
    switch (this.data.request.plan_type) {
      case 'mensual': return 30;
      case 'quincenal': return 15;
      default: return 7;
    }
  }

  ngOnInit(): void {
    const defaultEnd = new Date();
    defaultEnd.setDate(defaultEnd.getDate() + this.daysForPlan);
    defaultEnd.setHours(23, 59, 59, 999);
    this.customEndDateControl.setValue(defaultEnd);
  }

  formatPaymentMethod(m?: string): string {
    switch (m) {
      case 'deposito_bancario': return 'Depósito Banrural';
      case 'transferencia': return 'Transferencia Bancaria';
      case 'efectivo': return 'Efectivo';
      default: return 'Depósito Bancario';
    }
  }

  onConfirm(): void {
    const val = this.customEndDateControl.value;
    if (val) {
      val.setHours(23, 59, 59, 999);
    }
    this.dialogRef.close({ confirmed: true, customEndDate: val });
  }
}

// ── Diálogo: Rechazar Solicitud de Renovación ────────────────────────────────
@Component({
  selector: 'app-reject-renewal-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatIconModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule
  ],
  template: `
    <div class="reject-dialog">
      <div class="reject-dialog__header">
        <div class="reject-dialog__icon">
          <mat-icon>cancel</mat-icon>
        </div>
        <div>
          <h2 class="reject-dialog__title">Rechazar Renovación</h2>
          <p class="reject-dialog__subtitle">{{ data.request.driverName }} ({{ data.request.driverPlate }})</p>
        </div>
      </div>

      <div class="reject-dialog__body">
        <p class="prompt-text">
          Indica el motivo por el cual no se aprueba esta solicitud. El conductor podrá consultarlo en su historial:
        </p>
        <mat-form-field appearance="outline" class="full-field">
          <mat-label>Motivo del rechazo</mat-label>
          <textarea
            matInput
            rows="3"
            [formControl]="reasonControl"
            placeholder="Ej: Número de boleta no coincide o no aparece reflejado en la cuenta."
          ></textarea>
        </mat-form-field>
      </div>

      <div class="reject-dialog__actions">
        <button mat-button (click)="dialogRef.close(null)">Cancelar</button>
        <button mat-flat-button class="btn-reject" (click)="onConfirm()">
          <mat-icon>block</mat-icon>
          Rechazar Solicitud
        </button>
      </div>
    </div>
  `,
  styles: [`
    .reject-dialog {
      background: #1e293b;
      color: #f1f5f9;
      border-radius: 20px;
      padding: 0;
      overflow: hidden;
      max-width: 460px;
      width: 100%;
    }
    .reject-dialog__header {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 22px 24px;
      border-bottom: 1px solid rgba(255, 255, 255, 0.08);
    }
    .reject-dialog__icon {
      width: 46px;
      height: 46px;
      border-radius: 12px;
      background: rgba(239, 68, 68, 0.15);
      color: #f87171;
      border: 1px solid rgba(239, 68, 68, 0.3);
      display: flex;
      align-items: center;
      justify-content: center;
      mat-icon { font-size: 26px; width: 26px; height: 26px; }
    }
    .reject-dialog__title { margin: 0; font-size: 1.2rem; font-weight: 700; color: #f8fafc; }
    .reject-dialog__subtitle { margin: 2px 0 0; font-size: 0.85rem; color: #94a3b8; }
    .reject-dialog__body { padding: 20px 24px 8px; }
    .prompt-text { margin: 0 0 14px; font-size: 0.88rem; color: #cbd5e1; line-height: 1.4; }
    .full-field { width: 100%; }
    .reject-dialog__actions {
      display: flex;
      justify-content: flex-end;
      gap: 12px;
      padding: 16px 24px 20px;
      border-top: 1px solid rgba(255, 255, 255, 0.08);
      .btn-reject {
        background: linear-gradient(135deg, #dc2626, #ef4444);
        color: #ffffff;
        font-weight: 600;
        border-radius: 10px;
      }
    }
  `]
})
export class RejectRenewalDialogComponent {
  readonly data: { request: SubscriptionPaymentRequest } = inject(MAT_DIALOG_DATA);
  readonly dialogRef = inject(MatDialogRef<RejectRenewalDialogComponent>);
  readonly reasonControl = new FormControl<string>('');

  onConfirm(): void {
    this.dialogRef.close({ confirmed: true, reason: this.reasonControl.value?.trim() || 'Comprobante no válido o pago no confirmado.' });
  }
}

// ── Componente Principal de Suscripciones y Renovaciones ─────────────────────
@Component({
  selector: 'app-subscriptions',
  standalone: true,
  imports: [
    CommonModule,
    DecimalPipe,
    DatePipe,
    ReactiveFormsModule,
    MatTableModule,
    MatIconModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
    MatButtonModule,
    MatTabsModule,
    MatChipsModule,
    MatBadgeModule,
    MatSnackBarModule,
    MatFormFieldModule,
    MatInputModule
  ],
  template: `
    <div class="page-header">
      <div class="header-title">
        <h1>Gestión de Suscripciones y Renovaciones</h1>
        <p>Revisa solicitudes enviadas por conductores, valida comprobantes por WhatsApp y audita membresías activas.</p>
      </div>
      <div class="header-actions">
        <button mat-button class="refresh-btn" (click)="loadAllData()" [disabled]="isLoading()">
          <mat-icon>refresh</mat-icon>
          Actualizar
        </button>
        <button mat-flat-button class="export-btn" (click)="exportToCsv()" [disabled]="subscriptions().length === 0">
          <mat-icon>download</mat-icon>
          Exportar Historial CSV
        </button>
      </div>
    </div>

    <!-- Pestañas Principales -->
    <mat-tab-group class="custom-tabs" animationDuration="250ms">
      
      <!-- TAB 1: Solicitudes de Renovación -->
      <mat-tab>
        <ng-template mat-tab-label>
          <div class="tab-label-content">
            <mat-icon>receipt_long</mat-icon>
            <span>Solicitudes de Renovación</span>
            @if (pendingRequestsCount() > 0) {
              <span class="badge-pending">{{ pendingRequestsCount() }}</span>
            }
          </div>
        </ng-template>

        <div class="tab-content">
          <!-- KPI Cards Rápidos -->
          <div class="kpi-grid">
            <div class="kpi-card">
              <div class="kpi-card__icon kpi-card__icon--blue">
                <mat-icon>inbox</mat-icon>
              </div>
              <div class="kpi-card__data">
                <span class="kpi-card__label">Total Solicitudes</span>
                <span class="kpi-card__value">{{ paymentRequests().length }}</span>
              </div>
            </div>

            <div class="kpi-card kpi-card--highlight">
              <div class="kpi-card__icon kpi-card__icon--amber">
                <mat-icon>pending_actions</mat-icon>
              </div>
              <div class="kpi-card__data">
                <span class="kpi-card__label">Pendientes de Validar</span>
                <span class="kpi-card__value">{{ pendingRequestsCount() }}</span>
              </div>
            </div>

            <div class="kpi-card">
              <div class="kpi-card__icon kpi-card__icon--green">
                <mat-icon>task_alt</mat-icon>
              </div>
              <div class="kpi-card__data">
                <span class="kpi-card__label">Aprobadas</span>
                <span class="kpi-card__value">{{ approvedRequestsCount() }}</span>
              </div>
            </div>

            <div class="kpi-card">
              <div class="kpi-card__icon kpi-card__icon--red">
                <mat-icon>do_not_disturb_on</mat-icon>
              </div>
              <div class="kpi-card__data">
                <span class="kpi-card__label">Rechazadas</span>
                <span class="kpi-card__value">{{ rejectedRequestsCount() }}</span>
              </div>
            </div>
          </div>

          <!-- Filtros de Estado y Búsqueda -->
          <div class="filters-toolbar">
            <div class="chip-filter-group">
              <button 
                class="filter-chip-btn" 
                [class.active]="selectedRequestFilter() === 'todos'"
                (click)="selectedRequestFilter.set('todos')">
                Todas ({{ paymentRequests().length }})
              </button>
              <button 
                class="filter-chip-btn filter-chip-btn--pending" 
                [class.active]="selectedRequestFilter() === 'pendiente'"
                (click)="selectedRequestFilter.set('pendiente')">
                Pendientes ({{ pendingRequestsCount() }})
              </button>
              <button 
                class="filter-chip-btn filter-chip-btn--approved" 
                [class.active]="selectedRequestFilter() === 'aprobado'"
                (click)="selectedRequestFilter.set('aprobado')">
                Aprobadas ({{ approvedRequestsCount() }})
              </button>
              <button 
                class="filter-chip-btn filter-chip-btn--rejected" 
                [class.active]="selectedRequestFilter() === 'rechazado'"
                (click)="selectedRequestFilter.set('rechazado')">
                Rechazadas ({{ rejectedRequestsCount() }})
              </button>
            </div>

            <div class="search-box">
              <mat-icon class="search-icon">search</mat-icon>
              <input
                type="text"
                placeholder="Buscar por conductor, placa o boleta..."
                [formControl]="requestSearchControl"
              />
              @if (requestSearchControl.value) {
                <button class="clear-search-btn" (click)="requestSearchControl.setValue('')">
                  <mat-icon>close</mat-icon>
                </button>
              }
            </div>
          </div>

          <!-- Tabla de Solicitudes -->
          <div class="table-container mat-elevation-z8">
            @if (isLoadingRequests()) {
              <div class="loading-shade">
                <mat-spinner diameter="40"></mat-spinner>
              </div>
            }

            <table mat-table [dataSource]="filteredRequests()" class="full-width-table">

              <!-- Conductor -->
              <ng-container matColumnDef="conductor">
                <th mat-header-cell *matHeaderCellDef> Conductor </th>
                <td mat-cell *matCellDef="let req">
                  <div class="driver-info">
                    <div class="avatar">{{ req.driverName.charAt(0).toUpperCase() }}</div>
                    <div class="driver-details">
                      <span class="name">{{ req.driverName }}</span>
                      <div class="sub-tags">
                        <span class="plate-mini">{{ req.driverPlate }}</span>
                        @if (req.driverUnitNumber) {
                          <span class="unit-mini">TucTuc #{{ req.driverUnitNumber }}</span>
                        }
                      </div>
                    </div>
                  </div>
                </td>
              </ng-container>

              <!-- Contacto / WhatsApp -->
              <ng-container matColumnDef="contacto">
                <th mat-header-cell *matHeaderCellDef> Validar Captura </th>
                <td mat-cell *matCellDef="let req">
                  @if (req.driverPhone) {
                    <a
                      class="btn-wa-action"
                      [href]="getWhatsAppUrl(req)"
                      target="_blank"
                      matTooltip="Abrir WhatsApp para pedir/validar comprobante"
                    >
                      <mat-icon class="wa-icon">chat</mat-icon>
                      <span>{{ req.driverPhone }}</span>
                    </a>
                  } @else {
                    <span class="text-muted">Sin teléfono</span>
                  }
                </td>
              </ng-container>

              <!-- Plan y Monto -->
              <ng-container matColumnDef="plan">
                <th mat-header-cell *matHeaderCellDef> Plan y Cuota </th>
                <td mat-cell *matCellDef="let req">
                  <div class="plan-price-cell">
                    <span class="plan-badge plan-badge--{{ req.plan_type || 'semanal' }}">
                      {{ (req.plan_type || 'semanal') | uppercase }}
                    </span>
                    <span class="amount-value">Q {{ (req.amount || 0) | number:'1.2-2' }}</span>
                  </div>
                </td>
              </ng-container>

              <!-- Boleta / Referencia -->
              <ng-container matColumnDef="boleta">
                <th mat-header-cell *matHeaderCellDef> Comprobante / Pago </th>
                <td mat-cell *matCellDef="let req">
                  <div class="payment-cell">
                    <span class="payment-method">{{ formatPaymentMethod(req.payment_method) }}</span>
                    @if (req.reference_number) {
                      <div class="ref-container" matTooltip="Número de boleta o transferencia">
                        <mat-icon class="ref-icon">receipt</mat-icon>
                        <span class="ref-text">{{ req.reference_number }}</span>
                      </div>
                    } @else {
                      <span class="ref-text-empty">Sin número (validar captura)</span>
                    }
                  </div>
                </td>
              </ng-container>

              <!-- Fecha de Solicitud -->
              <ng-container matColumnDef="fecha">
                <th mat-header-cell *matHeaderCellDef> Solicitado </th>
                <td mat-cell *matCellDef="let req">
                  <div class="date-cell">
                    <mat-icon class="date-icon">schedule</mat-icon>
                    <span>{{ req.created_at | date:'dd/MM/yyyy HH:mm' }}</span>
                  </div>
                </td>
              </ng-container>

              <!-- Estado -->
              <ng-container matColumnDef="estado">
                <th mat-header-cell *matHeaderCellDef> Estado </th>
                <td mat-cell *matCellDef="let req">
                  @switch (req.status) {
                    @case ('pendiente') {
                      <span class="status-chip status-chip--pending">
                        <mat-icon>hourglass_top</mat-icon>
                        Pendiente
                      </span>
                    }
                    @case ('aprobado') {
                      <span class="status-chip status-chip--approved">
                        <mat-icon>check_circle</mat-icon>
                        Aprobado
                      </span>
                    }
                    @case ('rechazado') {
                      <span class="status-chip status-chip--rejected" [matTooltip]="req.rejection_reason || 'Rechazado por el administrador'">
                        <mat-icon>cancel</mat-icon>
                        Rechazado
                      </span>
                    }
                  }
                </td>
              </ng-container>

              <!-- Acciones de Aprobación -->
              <ng-container matColumnDef="acciones">
                <th mat-header-cell *matHeaderCellDef class="text-right"> Acciones </th>
                <td mat-cell *matCellDef="let req" class="text-right">
                  @if (req.status === 'pendiente') {
                    <div class="action-buttons">
                      <button
                        mat-flat-button
                        class="btn-table-approve"
                        (click)="openApproveDialog(req)"
                        matTooltip="Aprobar y extender suscripción"
                      >
                        <mat-icon>check</mat-icon>
                        Aceptar
                      </button>
                      <button
                        mat-button
                        class="btn-table-reject"
                        (click)="openRejectDialog(req)"
                        matTooltip="Rechazar solicitud"
                      >
                        <mat-icon>close</mat-icon>
                        Rechazar
                      </button>
                    </div>
                  } @else if (req.status === 'aprobado') {
                    <span class="resolved-note text-green">
                      <mat-icon>verified</mat-icon>
                      Aprobada {{ req.reviewed_at ? (req.reviewed_at | date:'dd/MM') : '' }}
                    </span>
                  } @else {
                    <span class="resolved-note text-red" [matTooltip]="req.rejection_reason || ''">
                      <mat-icon>info</mat-icon>
                      Rechazada
                    </span>
                  }
                </td>
              </ng-container>

              <tr mat-header-row *matHeaderRowDef="requestColumns"></tr>
              <tr mat-row *matRowDef="let row; columns: requestColumns;" class="element-row"></tr>

              <tr class="mat-row" *matNoDataRow>
                <td class="mat-cell empty-cell" colspan="7">
                  <mat-icon class="empty-icon">inbox</mat-icon>
                  <p>No se encontraron solicitudes con los filtros aplicados.</p>
                </td>
              </tr>
            </table>
          </div>
        </div>
      </mat-tab>

      <!-- TAB 2: Historial Global de Suscripciones -->
      <mat-tab>
        <ng-template mat-tab-label>
          <div class="tab-label-content">
            <mat-icon>history_edu</mat-icon>
            <span>Historial Global de Membresías</span>
          </div>
        </ng-template>

        <div class="tab-content">
          <div class="table-container mat-elevation-z8">
            @if (isLoadingSubscriptions()) {
              <div class="loading-shade">
                <mat-spinner diameter="40"></mat-spinner>
              </div>
            }

            <table mat-table [dataSource]="subscriptions()" class="full-width-table">

              <!-- Conductor -->
              <ng-container matColumnDef="conductor">
                <th mat-header-cell *matHeaderCellDef> Conductor </th>
                <td mat-cell *matCellDef="let sub"> 
                  <div class="driver-info">
                    <div class="avatar">{{ sub.driverName.charAt(0).toUpperCase() }}</div>
                    <span class="name">{{ sub.driverName }}</span>
                  </div>
                </td>
              </ng-container>

              <!-- Placas -->
              <ng-container matColumnDef="placas">
                <th mat-header-cell *matHeaderCellDef> Placas </th>
                <td mat-cell *matCellDef="let sub"> 
                  <span class="plate-badge">{{ sub.driverPlate }}</span>
                </td>
              </ng-container>

              <!-- Plan -->
              <ng-container matColumnDef="plan">
                <th mat-header-cell *matHeaderCellDef> Plan </th>
                <td mat-cell *matCellDef="let sub"> 
                  <span class="plan-badge plan-badge--{{ sub.plan_type || 'semanal' }}">
                    {{ (sub.plan_type || 'semanal') | uppercase }}
                  </span>
                </td>
              </ng-container>

              <!-- Monto -->
              <ng-container matColumnDef="monto">
                <th mat-header-cell *matHeaderCellDef> Monto Pagado </th>
                <td mat-cell *matCellDef="let sub"> 
                  <span class="amount-value">Q {{ (sub.amount_paid || 0) | number:'1.2-2' }}</span>
                </td>
              </ng-container>

              <!-- Método / Boleta -->
              <ng-container matColumnDef="pago">
                <th mat-header-cell *matHeaderCellDef> Método y Boleta </th>
                <td mat-cell *matCellDef="let sub"> 
                  <div class="payment-cell">
                    <span class="payment-method">{{ formatPaymentMethod(sub.payment_method) }}</span>
                    @if (sub.reference_number) {
                      <span class="reference-badge" matTooltip="Número de comprobante">
                        Ref: {{ sub.reference_number }}
                      </span>
                    }
                  </div>
                </td>
              </ng-container>

              <!-- Inicio -->
              <ng-container matColumnDef="inicio">
                <th mat-header-cell *matHeaderCellDef> Fecha de Inicio </th>
                <td mat-cell *matCellDef="let sub">
                  <div class="date-cell">
                    <mat-icon class="date-icon">event_available</mat-icon>
                    <span>{{ sub.start_date | date:'dd/MM/yyyy HH:mm' }}</span>
                  </div>
                </td>
              </ng-container>

              <!-- Vencimiento -->
              <ng-container matColumnDef="fin">
                <th mat-header-cell *matHeaderCellDef> Vencimiento </th>
                <td mat-cell *matCellDef="let sub"> 
                  <div class="date-cell">
                    <mat-icon class="date-icon">event_busy</mat-icon>
                    <span>{{ sub.end_date | date:'dd/MM/yyyy HH:mm' }}</span>
                  </div>
                </td>
              </ng-container>

              <!-- Estado -->
              <ng-container matColumnDef="estado">
                <th mat-header-cell *matHeaderCellDef> Estado </th>
                <td mat-cell *matCellDef="let sub"> 
                  <span class="status-badge" [class]="sub.status === 'Activo' ? 'status-active' : 'status-expired'">
                    {{ sub.status }}
                  </span>
                </td>
              </ng-container>

              <tr mat-header-row *matHeaderRowDef="subscriptionColumns"></tr>
              <tr mat-row *matRowDef="let row; columns: subscriptionColumns;" class="element-row"></tr>
              
              <tr class="mat-row" *matNoDataRow>
                <td class="mat-cell empty-cell" colspan="8">
                  <mat-icon class="empty-icon">history</mat-icon>
                  <p>No hay suscripciones registradas aún.</p>
                </td>
              </tr>
            </table>
          </div>
        </div>
      </mat-tab>

    </mat-tab-group>
  `,
  styles: [`
    :host {
      display: block;
      padding: 24px 32px;
    }

    .page-header {
      margin-bottom: 20px;
      display: flex;
      justify-content: space-between;
      align-items: flex-end;
      flex-wrap: wrap;
      gap: 16px;

      .header-title {
        h1 {
          margin: 0;
          font-size: 1.5rem;
          color: #f8fafc;
          font-weight: 700;
          letter-spacing: -0.3px;
        }
        p {
          margin: 4px 0 0 0;
          color: #94a3b8;
          font-size: 0.95rem;
        }
      }

      .header-actions {
        display: flex;
        align-items: center;
        gap: 10px;
      }

      .refresh-btn {
        color: #94a3b8;
        border: 1px solid rgba(255, 255, 255, 0.1);
        border-radius: 8px;
        height: 40px;
        &:hover {
          color: #f8fafc;
          background: rgba(255, 255, 255, 0.05);
        }
      }

      .export-btn {
        background: linear-gradient(135deg, #1e3a5f, #2563eb);
        color: #ffffff;
        font-weight: 600;
        border-radius: 8px;
        padding: 0 16px;
        height: 40px;
        display: flex;
        align-items: center;
        gap: 8px;
        transition: all 0.2s ease;

        &:hover:not([disabled]) {
          transform: translateY(-1px);
          box-shadow: 0 4px 12px rgba(37, 99, 235, 0.35);
        }

        &[disabled] {
          opacity: 0.4;
        }
      }
    }

    /* ── Pestañas Estilizadas ─────────────────────────────── */
    .custom-tabs {
      ::ng-deep .mat-mdc-tab-header {
        border-bottom: 1px solid rgba(255, 255, 255, 0.1);
        margin-bottom: 20px;
      }
      ::ng-deep .mat-mdc-tab .mdc-tab__text-label {
        color: #94a3b8;
        font-weight: 600;
        font-size: 0.95rem;
      }
      ::ng-deep .mat-mdc-tab.mdc-tab--active .mdc-tab__text-label {
        color: #f8fafc;
      }
      ::ng-deep .mat-mdc-tab-indicator .mdc-tab-indicator__content--underline {
        border-color: #38bdf8 !important;
        border-top-width: 3px !important;
      }
    }

    .tab-label-content {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 0 4px;
      mat-icon { font-size: 20px; width: 20px; height: 20px; }
    }

    .badge-pending {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-width: 20px;
      height: 20px;
      padding: 0 6px;
      border-radius: 10px;
      background: #f59e0b;
      color: #0f172a;
      font-size: 0.75rem;
      font-weight: 700;
      animation: pulse-badge 2s infinite ease-in-out;
    }

    @keyframes pulse-badge {
      0%, 100% { transform: scale(1); }
      50% { transform: scale(1.1); box-shadow: 0 0 10px rgba(245, 158, 11, 0.6); }
    }

    .tab-content {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    /* ── KPI Grid ─────────────────────────────────────────── */
    .kpi-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
      gap: 16px;
    }

    .kpi-card {
      background: rgba(30, 41, 59, 0.6);
      border: 1px solid rgba(255, 255, 255, 0.08);
      border-radius: 14px;
      padding: 16px 18px;
      display: flex;
      align-items: center;
      gap: 16px;
      transition: transform 0.2s, border-color 0.2s;

      &:hover {
        border-color: rgba(255, 255, 255, 0.18);
        transform: translateY(-2px);
      }
    }

    .kpi-card--highlight {
      background: rgba(245, 158, 11, 0.08);
      border-color: rgba(245, 158, 11, 0.3);
    }

    .kpi-card__icon {
      width: 44px;
      height: 44px;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;

      mat-icon { font-size: 24px; width: 24px; height: 24px; }
    }

    .kpi-card__icon--blue {
      background: rgba(56, 189, 248, 0.15);
      color: #38bdf8;
      border: 1px solid rgba(56, 189, 248, 0.3);
    }
    .kpi-card__icon--amber {
      background: rgba(245, 158, 11, 0.2);
      color: #fbbf24;
      border: 1px solid rgba(245, 158, 11, 0.4);
    }
    .kpi-card__icon--green {
      background: rgba(34, 197, 94, 0.15);
      color: #4ade80;
      border: 1px solid rgba(34, 197, 94, 0.3);
    }
    .kpi-card__icon--red {
      background: rgba(239, 68, 68, 0.15);
      color: #f87171;
      border: 1px solid rgba(239, 68, 68, 0.3);
    }

    .kpi-card__data {
      display: flex;
      flex-direction: column;
    }
    .kpi-card__label {
      font-size: 0.8rem;
      color: #94a3b8;
      font-weight: 500;
    }
    .kpi-card__value {
      font-size: 1.5rem;
      font-weight: 800;
      color: #f8fafc;
      line-height: 1.2;
    }

    /* ── Filtros y Búsqueda ───────────────────────────────── */
    .filters-toolbar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      flex-wrap: wrap;
      gap: 12px;
    }

    .chip-filter-group {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
    }

    .filter-chip-btn {
      background: rgba(15, 23, 42, 0.7);
      border: 1px solid rgba(255, 255, 255, 0.1);
      color: #94a3b8;
      font-size: 0.82rem;
      font-weight: 600;
      padding: 6px 14px;
      border-radius: 20px;
      cursor: pointer;
      transition: all 0.15s ease;

      &:hover {
        background: rgba(255, 255, 255, 0.08);
        color: #f8fafc;
      }

      &.active {
        background: #38bdf8;
        border-color: #38bdf8;
        color: #0f172a;
        font-weight: 700;
      }
    }

    .filter-chip-btn--pending.active {
      background: #f59e0b;
      border-color: #f59e0b;
      color: #0f172a;
    }

    .filter-chip-btn--approved.active {
      background: #22c55e;
      border-color: #22c55e;
      color: #0f172a;
    }

    .filter-chip-btn--rejected.active {
      background: #ef4444;
      border-color: #ef4444;
      color: #ffffff;
    }

    .search-box {
      position: relative;
      display: flex;
      align-items: center;
      background: rgba(15, 23, 42, 0.7);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 10px;
      padding: 4px 12px;
      width: 320px;
      max-width: 100%;

      .search-icon {
        color: #64748b;
        font-size: 20px;
        width: 20px;
        height: 20px;
        margin-right: 8px;
      }

      input {
        background: transparent;
        border: none;
        outline: none;
        color: #f8fafc;
        font-size: 0.88rem;
        width: 100%;
        &::placeholder { color: #64748b; }
      }

      .clear-search-btn {
        background: transparent;
        border: none;
        color: #64748b;
        cursor: pointer;
        padding: 0;
        display: flex;
        align-items: center;
        &:hover { color: #f8fafc; }
        mat-icon { font-size: 16px; width: 16px; height: 16px; }
      }
    }

    /* ── Tablas ───────────────────────────────────────────── */
    .table-container {
      position: relative;
      background: rgba(30, 41, 59, 0.7);
      border-radius: 12px;
      overflow-x: auto;
      overflow-y: hidden;
      -webkit-overflow-scrolling: touch;
      border: 1px solid rgba(255, 255, 255, 0.1);
    }

    .loading-shade {
      position: absolute;
      top: 0;
      left: 0;
      bottom: 0;
      right: 0;
      background: rgba(15, 23, 42, 0.65);
      z-index: 10;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .full-width-table {
      width: 100%;
      min-width: 820px;
      background: transparent;
    }

    ::ng-deep .mat-mdc-header-cell {
      color: #94a3b8 !important;
      font-weight: 600 !important;
      border-bottom-color: rgba(255, 255, 255, 0.1) !important;
      padding: 14px 16px !important;
      font-size: 0.82rem !important;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    ::ng-deep .mat-mdc-cell {
      color: #e2e8f0 !important;
      border-bottom-color: rgba(255, 255, 255, 0.05) !important;
      padding: 14px 16px !important;
    }

    .text-right { text-align: right; }

    .element-row:hover {
      background: rgba(255, 255, 255, 0.03);
    }

    .driver-info {
      display: flex;
      align-items: center;
      gap: 12px;

      .avatar {
        width: 38px;
        height: 38px;
        border-radius: 12px;
        background: linear-gradient(135deg, #fb923c, #ea580c);
        color: white;
        display: flex;
        align-items: center;
        justify-content: center;
        font-weight: 700;
        font-size: 1.1rem;
        flex-shrink: 0;
      }
      .driver-details {
        display: flex;
        flex-direction: column;
        gap: 2px;
      }
      .name {
        font-weight: 600;
        color: #f8fafc;
        font-size: 0.95rem;
      }
      .sub-tags {
        display: flex;
        gap: 6px;
        align-items: center;
      }
      .plate-mini {
        font-family: monospace;
        font-size: 0.72rem;
        background: #1e293b;
        color: #94a3b8;
        border: 1px solid #334155;
        padding: 1px 6px;
        border-radius: 4px;
      }
      .unit-mini {
        font-size: 0.72rem;
        background: rgba(249, 212, 35, 0.15);
        color: #f9d423;
        border: 1px solid rgba(249, 212, 35, 0.3);
        padding: 1px 6px;
        border-radius: 4px;
        font-weight: 600;
      }
    }

    .btn-wa-action {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      background: rgba(37, 211, 102, 0.12);
      border: 1px solid rgba(37, 211, 102, 0.3);
      color: #25d366;
      padding: 5px 10px;
      border-radius: 8px;
      font-size: 0.82rem;
      font-weight: 600;
      text-decoration: none;
      transition: all 0.15s ease;

      &:hover {
        background: #25d366;
        color: #0f2027;
        box-shadow: 0 2px 8px rgba(37, 211, 102, 0.4);
      }

      .wa-icon { font-size: 16px; width: 16px; height: 16px; }
    }

    .plan-price-cell {
      display: flex;
      flex-direction: column;
      gap: 3px;
    }

    .plan-badge {
      display: inline-flex;
      padding: 3px 8px;
      border-radius: 6px;
      font-size: 0.72rem;
      font-weight: 700;
      letter-spacing: 0.5px;
      background: rgba(99, 102, 241, 0.15);
      color: #818cf8;
      border: 1px solid rgba(99, 102, 241, 0.3);
      width: fit-content;

      &.plan-badge--mensual {
        background: rgba(34, 197, 94, 0.15);
        color: #4ade80;
        border-color: rgba(34, 197, 94, 0.3);
      }
      &.plan-badge--quincenal {
        background: rgba(249, 212, 35, 0.15);
        color: #f9d423;
        border-color: rgba(249, 212, 35, 0.3);
      }
    }

    .amount-value {
      font-weight: 700;
      color: #4ade80;
      font-size: 0.95rem;
    }

    .payment-cell {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    .payment-method {
      font-size: 0.85rem;
      color: #e2e8f0;
      font-weight: 500;
    }
    .ref-container {
      display: flex;
      align-items: center;
      gap: 4px;
      color: #38bdf8;
      font-size: 0.82rem;
      font-family: monospace;
      font-weight: 600;
      .ref-icon { font-size: 14px; width: 14px; height: 14px; }
    }
    .ref-text-empty {
      font-size: 0.75rem;
      color: #64748b;
      font-style: italic;
    }
    .text-muted { color: #64748b; font-size: 0.85rem; }

    .date-cell {
      display: flex;
      align-items: center;
      gap: 6px;
      color: #cbd5e1;
      font-size: 0.85rem;
      .date-icon { font-size: 16px; width: 16px; height: 16px; color: #94a3b8; }
    }

    .status-chip {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      padding: 4px 10px;
      border-radius: 20px;
      font-size: 0.75rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.4px;
      mat-icon { font-size: 14px; width: 14px; height: 14px; }
    }

    .status-chip--pending {
      background: rgba(245, 158, 11, 0.15);
      color: #fbbf24;
      border: 1px solid rgba(245, 158, 11, 0.35);
    }
    .status-chip--approved {
      background: rgba(34, 197, 94, 0.15);
      color: #4ade80;
      border: 1px solid rgba(34, 197, 94, 0.35);
    }
    .status-chip--rejected {
      background: rgba(239, 68, 68, 0.15);
      color: #f87171;
      border: 1px solid rgba(239, 68, 68, 0.35);
    }

    .action-buttons {
      display: flex;
      justify-content: flex-end;
      gap: 6px;
    }

    .btn-table-approve {
      background: linear-gradient(135deg, #16a34a, #22c55e);
      color: #ffffff;
      font-weight: 600;
      font-size: 0.8rem;
      padding: 0 12px;
      height: 32px;
      border-radius: 6px;
      display: flex;
      align-items: center;
      gap: 4px;
      mat-icon { font-size: 16px; width: 16px; height: 16px; }
      &:hover { box-shadow: 0 2px 8px rgba(34, 197, 94, 0.4); }
    }

    .btn-table-reject {
      color: #f87171;
      font-weight: 600;
      font-size: 0.8rem;
      padding: 0 8px;
      height: 32px;
      border-radius: 6px;
      border: 1px solid rgba(239, 68, 68, 0.3);
      display: flex;
      align-items: center;
      gap: 4px;
      mat-icon { font-size: 16px; width: 16px; height: 16px; }
      &:hover { background: rgba(239, 68, 68, 0.1); }
    }

    .resolved-note {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      font-size: 0.78rem;
      font-weight: 600;
      mat-icon { font-size: 16px; width: 16px; height: 16px; }
      &.text-green { color: #4ade80; }
      &.text-red { color: #f87171; }
    }

    /* ── Historial Tab Styling ────────────────────────────── */
    .plate-badge {
      display: inline-flex;
      align-items: center;
      background: #1e293b;
      border: 1px solid #334155;
      padding: 4px 10px;
      border-radius: 6px;
      font-family: monospace;
      font-weight: 600;
      color: #cbd5e1;
      letter-spacing: 1px;
    }

    .reference-badge {
      font-size: 0.72rem;
      color: #94a3b8;
      font-family: monospace;
    }

    .status-badge {
      display: inline-flex;
      padding: 4px 10px;
      border-radius: 20px;
      font-size: 0.75rem;
      font-weight: 600;
      letter-spacing: 0.5px;
      text-transform: uppercase;

      &.status-active {
        background: rgba(34, 197, 94, 0.1);
        color: #4ade80;
        border: 1px solid rgba(34, 197, 94, 0.2);
      }
      &.status-expired {
        background: rgba(239, 68, 68, 0.1);
        color: #f87171;
        border: 1px solid rgba(239, 68, 68, 0.2);
      }
    }

    .empty-cell {
      text-align: center;
      padding: 48px !important;
      color: #64748b;
      .empty-icon { font-size: 48px; width: 48px; height: 48px; margin-bottom: 16px; opacity: 0.5; }
      p { margin: 0; font-size: 1.1rem; }
    }

    @media (max-width: 991.98px) {
      :host { padding: 18px 16px; }
    }
    @media (max-width: 640px) {
      :host { padding: 12px 10px; }
      .page-header { flex-direction: column; align-items: stretch; gap: 14px; }
      .filters-toolbar { flex-direction: column; align-items: stretch; }
      .search-box { width: 100%; }
      .kpi-grid { grid-template-columns: 1fr; }
    }
  `]
})
export class SubscriptionsComponent implements OnInit, OnDestroy {
  private readonly supabaseService = inject(SupabaseService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly subs = new Subscription();

  // Tab 1: Solicitudes de Renovación
  readonly requestColumns: string[] = ['conductor', 'contacto', 'plan', 'boleta', 'fecha', 'estado', 'acciones'];
  readonly paymentRequests = signal<SubscriptionPaymentRequest[]>([]);
  readonly isLoadingRequests = signal(true);
  readonly selectedRequestFilter = signal<'todos' | 'pendiente' | 'aprobado' | 'rechazado'>('todos');
  readonly requestSearchControl = new FormControl<string>('', { nonNullable: true });

  // Tab 2: Historial de Membresías
  readonly subscriptionColumns: string[] = ['conductor', 'placas', 'plan', 'monto', 'pago', 'inicio', 'fin', 'estado'];
  readonly subscriptions = signal<SubscriptionHistory[]>([]);
  readonly isLoadingSubscriptions = signal(true);

  // KPIs computados
  readonly pendingRequestsCount = computed(() =>
    this.paymentRequests().filter(r => r.status === 'pendiente').length
  );
  readonly approvedRequestsCount = computed(() =>
    this.paymentRequests().filter(r => r.status === 'aprobado').length
  );
  readonly rejectedRequestsCount = computed(() =>
    this.paymentRequests().filter(r => r.status === 'rechazado').length
  );

  // Lista filtrada reactivamente
  readonly filteredRequests = computed(() => {
    const list = this.paymentRequests();
    const filter = this.selectedRequestFilter();
    const query = (this.requestSearchControl.value || '').toLowerCase().trim();

    return list.filter(r => {
      // Filtro por estado
      if (filter !== 'todos' && r.status !== filter) return false;

      // Filtro por texto
      if (query) {
        const nameMatch = (r.driverName || '').toLowerCase().includes(query);
        const plateMatch = (r.driverPlate || '').toLowerCase().includes(query);
        const unitMatch = (r.driverUnitNumber || '').toLowerCase().includes(query);
        const refMatch = (r.reference_number || '').toLowerCase().includes(query);
        const phoneMatch = (r.driverPhone || '').toLowerCase().includes(query);
        if (!nameMatch && !plateMatch && !unitMatch && !refMatch && !phoneMatch) {
          return false;
        }
      }

      return true;
    });
  });

  isLoading = computed(() => this.isLoadingRequests() || this.isLoadingSubscriptions());

  async ngOnInit(): Promise<void> {
    await this.loadAllData();

    // Sincronización en tiempo real reactiva
    this.subs.add(
      this.supabaseService.getPaymentRequestsChanges$().subscribe(() => {
        this.loadRequests();
        this.loadSubscriptions();
      })
    );
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
  }

  async loadAllData(): Promise<void> {
    await Promise.all([
      this.loadRequests(),
      this.loadSubscriptions()
    ]);
  }

  async loadRequests(): Promise<void> {
    this.isLoadingRequests.set(true);
    try {
      const data = await this.supabaseService.getPaymentRequests();
      this.paymentRequests.set(data);
      await this.supabaseService.refreshPendingPaymentRequestsCount();
    } catch (e) {
      console.error('Error cargando solicitudes:', e);
      this.snackBar.open('Error al cargar solicitudes de renovación', 'Cerrar', { duration: 3000 });
    } finally {
      this.isLoadingRequests.set(false);
    }
  }

  async loadSubscriptions(): Promise<void> {
    this.isLoadingSubscriptions.set(true);
    try {
      const data = await this.supabaseService.getAllSubscriptions();
      this.subscriptions.set(data);
    } catch (e) {
      console.error('Error cargando suscripciones:', e);
    } finally {
      this.isLoadingSubscriptions.set(false);
    }
  }

  formatPaymentMethod(method?: string): string {
    switch (method) {
      case 'deposito_bancario': return 'Depósito Banrural';
      case 'transferencia': return 'Transferencia';
      case 'efectivo': return 'Efectivo';
      case 'gratis_prueba': return 'Prueba Gratis';
      default: return 'Depósito Bancario';
    }
  }

  getWhatsAppUrl(req: SubscriptionPaymentRequest): string {
    const rawPhone = (req.driverPhone || '').replace(/[^0-9]/g, '');
    const cleanPhone = rawPhone.length === 8 ? `502${rawPhone}` : rawPhone;
    const planName = (req.plan_type || 'semanal').toUpperCase();
    const boletaText = req.reference_number ? ` con boleta N° ${req.reference_number}` : '';
    const text = `Hola ${req.driverName}, te saludamos del soporte administrativo de TucTucMap. Vemos tu solicitud de renovación de suscripción ${planName}${boletaText}. Por favor envíanos la captura o foto del comprobante para validarla y activarte de inmediato.`;
    return `https://wa.me/${cleanPhone}?text=${encodeURIComponent(text)}`;
  }

  openApproveDialog(req: SubscriptionPaymentRequest): void {
    const dialogRef = this.dialog.open(ApproveRenewalDialogComponent, {
      data: { request: req },
      width: '480px',
      panelClass: 'dark-dialog-container'
    });

    dialogRef.afterClosed().subscribe(async (result) => {
      if (!result?.confirmed) return;
      try {
        await this.supabaseService.approvePaymentRequest(req.id, result.customEndDate);
        this.snackBar.open(`¡Renovación de ${req.driverName} aprobada con éxito!`, 'OK', {
          duration: 4000,
          panelClass: 'snackbar-success'
        });
        await this.loadAllData();
      } catch (err: any) {
        console.error('Error aprobando renovación:', err);
        this.snackBar.open(`Error: ${err?.message || 'No se pudo aprobar la renovación'}`, 'Cerrar', {
          duration: 5000,
          panelClass: 'snackbar-error'
        });
      }
    });
  }

  openRejectDialog(req: SubscriptionPaymentRequest): void {
    const dialogRef = this.dialog.open(RejectRenewalDialogComponent, {
      data: { request: req },
      width: '460px',
      panelClass: 'dark-dialog-container'
    });

    dialogRef.afterClosed().subscribe(async (result) => {
      if (!result?.confirmed) return;
      try {
        await this.supabaseService.rejectPaymentRequest(req.id, result.reason);
        this.snackBar.open(`Solicitud de ${req.driverName} rechazada.`, 'OK', {
          duration: 4000
        });
        await this.loadAllData();
      } catch (err: any) {
        console.error('Error rechazando solicitud:', err);
        this.snackBar.open(`Error: ${err?.message || 'No se pudo rechazar la solicitud'}`, 'Cerrar', {
          duration: 5000
        });
      }
    });
  }

  exportToCsv(): void {
    const list = this.subscriptions();
    if (!list || list.length === 0) return;

    const headers = ['ID', 'Conductor', 'Placas', 'Plan', 'Monto Pagado', 'Metodo', 'Referencia', 'Fecha Inicio', 'Fecha Fin'];
    const rows = list.map(s => [
      `"${s.id || ''}"`,
      `"${(s.driverName || '').replace(/"/g, '""')}"`,
      `"${s.driverPlate || ''}"`,
      `"${s.plan_type || ''}"`,
      `"${s.amount_paid != null ? s.amount_paid.toFixed(2) : '0.00'}"`,
      `"${this.formatPaymentMethod(s.payment_method)}"`,
      `"${(s.reference_number || '').replace(/"/g, '""')}"`,
      `"${s.start_date || ''}"`,
      `"${s.end_date || ''}"`
    ]);

    const csvContent = '\uFEFF' + [headers.join(','), ...rows.map(r => r.join(','))].join('\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.setAttribute('href', url);
    link.setAttribute('download', `suscripciones_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }
}
