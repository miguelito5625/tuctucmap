import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatTableModule } from '@angular/material/table';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { SupabaseService } from '../../services/supabase.service';
import { SubscriptionHistory } from '../../core/models/driver.model';

@Component({
  selector: 'app-driver-history-dialog',
  standalone: true,
  imports: [
    CommonModule,
    MatDialogModule,
    MatTableModule,
    MatIconModule,
    MatButtonModule,
    MatProgressSpinnerModule
  ],
  template: `
    <h2 mat-dialog-title class="dialog-title">
      <mat-icon class="title-icon">history</mat-icon>
      Historial de Suscripciones
    </h2>
    <div mat-dialog-content class="dialog-content">
      <p class="subtitle">Conductor: <strong>{{ data.driverName }}</strong></p>
      
      @if (isLoading()) {
        <div class="loading-shade">
          <mat-spinner diameter="32"></mat-spinner>
        </div>
      }

      <table mat-table [dataSource]="subscriptions()" class="full-width-table">
        <!-- Inicio -->
        <ng-container matColumnDef="inicio">
          <th mat-header-cell *matHeaderCellDef> Inicio </th>
          <td mat-cell *matCellDef="let sub"> {{ sub.start_date | date:'dd/MM/yyyy' }} </td>
        </ng-container>

        <!-- Vencimiento -->
        <ng-container matColumnDef="fin">
          <th mat-header-cell *matHeaderCellDef> Vencimiento </th>
          <td mat-cell *matCellDef="let sub"> {{ sub.end_date | date:'dd/MM/yyyy' }} </td>
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
          <td class="mat-cell empty-cell" colspan="3">
            <mat-icon class="empty-icon">history_toggle_off</mat-icon>
            <p>Este conductor no tiene renovaciones.</p>
          </td>
        </tr>
      </table>
    </div>
    <div mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cerrar</button>
    </div>
  `,
  styles: [`
    .dialog-title {
      display: flex;
      align-items: center;
      gap: 12px;
      color: #f8fafc;
      font-weight: 600;
      .title-icon {
        color: #fb923c;
      }
    }
    .dialog-content {
      position: relative;
      min-height: 200px;
      padding: 0 24px;
      overflow-x: auto;
      -webkit-overflow-scrolling: touch;
      max-width: 100%;
    }
    @media (max-width: 480px) {
      .dialog-content {
        padding: 0 12px;
      }
    }
    .subtitle {
      color: #94a3b8;
      margin-bottom: 16px;
      strong { color: #f1f5f9; }
    }
    .full-width-table {
      width: 100%;
      min-width: 360px;
      background: transparent;
    }
    ::ng-deep .mat-mdc-header-cell {
      color: #94a3b8 !important;
      font-weight: 600 !important;
      border-bottom-color: rgba(255, 255, 255, 0.1) !important;
    }
    ::ng-deep .mat-mdc-cell {
      color: #e2e8f0 !important;
      border-bottom-color: rgba(255, 255, 255, 0.05) !important;
    }
    .element-row:hover {
      background: rgba(255, 255, 255, 0.03);
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
    .loading-shade {
      position: absolute;
      top: 0;
      left: 0;
      bottom: 0;
      right: 0;
      background: rgba(15, 23, 42, 0.8);
      z-index: 1;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .empty-cell {
      text-align: center;
      padding: 32px !important;
      color: #64748b;
      .empty-icon {
        font-size: 36px;
        width: 36px;
        height: 36px;
        margin-bottom: 12px;
        opacity: 0.5;
      }
      p { margin: 0; }
    }
  `]
})
export class DriverHistoryDialogComponent implements OnInit {
  readonly dialogRef = inject(MatDialogRef<DriverHistoryDialogComponent>);
  readonly data = inject<{ driverId: string, driverName: string }>(MAT_DIALOG_DATA);
  private readonly supabaseService = inject(SupabaseService);

  readonly displayedColumns: string[] = ['inicio', 'fin', 'estado'];
  readonly subscriptions = signal<SubscriptionHistory[]>([]);
  readonly isLoading = signal(true);

  async ngOnInit(): Promise<void> {
    const data = await this.supabaseService.getDriverSubscriptions(this.data.driverId);
    this.subscriptions.set(data);
    this.isLoading.set(false);
  }
}
