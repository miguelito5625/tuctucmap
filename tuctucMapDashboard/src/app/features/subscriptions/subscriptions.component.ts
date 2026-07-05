import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { SupabaseService } from '../../services/supabase.service';
import { SubscriptionHistory } from '../../core/models/driver.model';

@Component({
  selector: 'app-subscriptions',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatIconModule,
    MatTooltipModule,
    MatProgressSpinnerModule
  ],
  template: `
    <div class="page-header">
      <div class="header-title">
        <h1>Historial Global de Suscripciones</h1>
        <p>Registro de todas las renovaciones y suscripciones otorgadas a los conductores en la plataforma.</p>
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
          <td class="mat-cell empty-cell" colspan="5">
            <mat-icon class="empty-icon">history</mat-icon>
            <p>No hay suscripciones registradas aún.</p>
          </td>
        </tr>
      </table>
    </div>
  `,
  styles: [`
    .page-header {
      margin-bottom: 24px;
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
    }

    .table-container {
      position: relative;
      background: rgba(30, 41, 59, 0.7);
      border-radius: 12px;
      overflow: hidden;
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
  `]
})
export class SubscriptionsComponent implements OnInit {
  private readonly supabaseService = inject(SupabaseService);

  readonly displayedColumns: string[] = ['conductor', 'placas', 'inicio', 'fin', 'estado'];
  readonly subscriptions = signal<SubscriptionHistory[]>([]);
  readonly isLoading = signal(true);

  async ngOnInit(): Promise<void> {
    this.isLoading.set(true);
    const data = await this.supabaseService.getAllSubscriptions();
    this.subscriptions.set(data);
    this.isLoading.set(false);
  }
}
