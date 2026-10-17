import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatButtonModule } from '@angular/material/button';
import { SupabaseService } from '../../services/supabase.service';
import { SubscriptionHistory } from '../../core/models/driver.model';

@Component({
  selector: 'app-subscriptions',
  standalone: true,
  imports: [
    CommonModule,
    DecimalPipe,
    MatTableModule,
    MatIconModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
    MatButtonModule
  ],
  template: `
    <div class="page-header">
      <div class="header-title">
        <h1>Historial Global de Suscripciones y Pagos</h1>
        <p>Auditoría de planes, comprobantes de pago e ingresos recaudados de los conductores.</p>
      </div>
      <div class="header-actions">
        <button mat-flat-button class="export-btn" (click)="exportToCsv()" [disabled]="subscriptions().length === 0">
          <mat-icon>download</mat-icon>
          Exportar CSV
        </button>
      </div>
    </div>

    <div class="table-container mat-elevation-z8">
      @if (isLoading()) {
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

        <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: displayedColumns;" class="element-row"></tr>
        
        <!-- Empty State -->
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell empty-cell" colspan="8">
            <mat-icon class="empty-icon">history</mat-icon>
            <p>No hay suscripciones registradas aún.</p>
          </td>
        </tr>
      </table>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      padding: 24px 32px;
    }

    .page-header {
      margin-bottom: 24px;
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
          font-weight: 600;
        }
        p {
          margin: 4px 0 0 0;
          color: #94a3b8;
          font-size: 0.95rem;
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
      background: rgba(15, 23, 42, 0.6);
      z-index: 1;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .full-width-table {
      width: 100%;
      min-width: 760px;
      background: transparent;
    }

    ::ng-deep .mat-mdc-header-cell {
      color: #94a3b8 !important;
      font-weight: 600 !important;
      border-bottom-color: rgba(255, 255, 255, 0.1) !important;
      padding: 16px !important;
    }

    ::ng-deep .mat-mdc-cell {
      color: #e2e8f0 !important;
      border-bottom-color: rgba(255, 255, 255, 0.05) !important;
      padding: 16px !important;
    }

    .element-row:hover {
      background: rgba(255, 255, 255, 0.03);
    }

    .driver-info {
      display: flex;
      align-items: center;
      gap: 12px;

      .avatar {
        width: 36px;
        height: 36px;
        border-radius: 12px;
        background: linear-gradient(135deg, #fb923c, #ea580c);
        color: white;
        display: flex;
        align-items: center;
        justify-content: center;
        font-weight: 600;
        font-size: 1.1rem;
      }
      .name {
        font-weight: 500;
      }
    }

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

    .reference-badge {
      font-size: 0.72rem;
      color: #94a3b8;
      font-family: monospace;
    }

    .date-cell {
      display: flex;
      align-items: center;
      gap: 8px;
      color: #cbd5e1;
      
      .date-icon {
        font-size: 18px;
        width: 18px;
        height: 18px;
        color: #94a3b8;
      }
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
      
      .empty-icon {
        font-size: 48px;
        width: 48px;
        height: 48px;
        margin-bottom: 16px;
        opacity: 0.5;
      }
      
      p {
        margin: 0;
        font-size: 1.1rem;
      }
    }

    /* ── Responsive Media Queries ─────────────────────────── */
    @media (max-width: 991.98px) {
      :host {
        padding: 18px 16px;
      }
    }

    @media (max-width: 640px) {
      :host {
        padding: 12px 10px;
      }

      .page-header {
        flex-direction: column;
        align-items: stretch;
        gap: 14px;
      }

      .header-title h1 {
        font-size: 1.25rem;
      }

      .export-btn {
        width: 100%;
        justify-content: center;
      }
    }
  `]
})
export class SubscriptionsComponent implements OnInit {
  private readonly supabaseService = inject(SupabaseService);

  readonly displayedColumns: string[] = ['conductor', 'placas', 'plan', 'monto', 'pago', 'inicio', 'fin', 'estado'];
  readonly subscriptions = signal<SubscriptionHistory[]>([]);
  readonly isLoading = signal(true);

  async ngOnInit(): Promise<void> {
    this.isLoading.set(true);
    const data = await this.supabaseService.getAllSubscriptions();
    this.subscriptions.set(data);
    this.isLoading.set(false);
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
