import { Component, OnInit, ViewChild, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { MatPaginator, MatPaginatorModule } from '@angular/material/paginator';
import { MatSort, MatSortModule } from '@angular/material/sort';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { SupabaseService } from '../../services/supabase.service';
import { PassengerProfile } from '../../core/models/driver.model';

@Component({
  selector: 'app-passengers',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatTooltipModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  template: `
    <div class="passengers-container">
      <div class="header">
        <div class="title-group">
          <div class="icon-wrap">
            <mat-icon>people</mat-icon>
          </div>
          <div>
            <h2>Directorio de Pasajeros</h2>
            <p class="subtitle">Usuarios registrados en la aplicación para solicitar mototaxis</p>
          </div>
        </div>

        <div class="actions-group">
          <button mat-flat-button class="refresh-btn" (click)="loadPassengers()">
            <mat-icon>refresh</mat-icon>
            Actualizar
          </button>
        </div>
      </div>

      <div class="filter-row">
        <mat-form-field appearance="outline" class="search-field">
          <mat-icon matPrefix>search</mat-icon>
          <input matInput (keyup)="applyFilter($event)" placeholder="Buscar por nombre, teléfono o correo..." />
        </mat-form-field>
        <div class="stats-badge">
          <span>Total Pasajeros: <b>{{ totalCount() }}</b></span>
        </div>
      </div>

      <mat-card class="table-card">
        <div class="table-container">
          <table mat-table [dataSource]="dataSource" matSort class="dark-table">

            <!-- Name Column -->
            <ng-container matColumnDef="name">
              <th mat-header-cell *matHeaderCellDef mat-sort-header> Pasajero </th>
              <td mat-cell *matCellDef="let p">
                <div class="user-cell">
                  <div class="avatar">{{ (p.name || 'P').charAt(0).toUpperCase() }}</div>
                  <div class="user-details">
                    <span class="user-name">{{ p.name }}</span>
                    <span class="user-id">ID: {{ p.id | slice:0:8 }}...</span>
                  </div>
                </div>
              </td>
            </ng-container>

            <!-- Phone Column -->
            <ng-container matColumnDef="phone">
              <th mat-header-cell *matHeaderCellDef mat-sort-header> Teléfono </th>
              <td mat-cell *matCellDef="let p">
                <span class="phone-text">{{ p.phone || 'Sin número' }}</span>
              </td>
            </ng-container>

            <!-- Email Column -->
            <ng-container matColumnDef="email">
              <th mat-header-cell *matHeaderCellDef mat-sort-header> Correo </th>
              <td mat-cell *matCellDef="let p">
                <span class="email-text">{{ p.email }}</span>
              </td>
            </ng-container>

            <!-- Trips Count Column -->
            <ng-container matColumnDef="tripsCount">
              <th mat-header-cell *matHeaderCellDef mat-sort-header> Viajes Realizados </th>
              <td mat-cell *matCellDef="let p">
                <span class="trips-badge">{{ p.tripsCount || 0 }} viajes</span>
              </td>
            </ng-container>

            <!-- Created At Column -->
            <ng-container matColumnDef="created_at">
              <th mat-header-cell *matHeaderCellDef mat-sort-header> Fecha Registro </th>
              <td mat-cell *matCellDef="let p">
                <span class="date-text">{{ p.created_at | date:'dd/MM/yyyy' }}</span>
              </td>
            </ng-container>

            <!-- Actions Column -->
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef> Contacto </th>
              <td mat-cell *matCellDef="let p">
                @if (p.phone) {
                  <button
                    mat-icon-button
                    class="whatsapp-btn"
                    matTooltip="Contactar por WhatsApp"
                    (click)="openWhatsApp(p)"
                  >
                    <mat-icon>chat</mat-icon>
                  </button>
                } @else {
                  <span class="no-action">-</span>
                }
              </td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>

            <!-- No data row -->
            <tr class="mat-row" *matNoDataRow>
              <td class="mat-cell empty-cell" colspan="6">
                <div class="empty-state">
                  <mat-icon>person_off</mat-icon>
                  <p>No se encontraron pasajeros registrados</p>
                </div>
              </td>
            </tr>
          </table>
        </div>

        <mat-paginator
          [pageSizeOptions]="[10, 25, 50]"
          showFirstLastButtons
          class="dark-paginator"
        ></mat-paginator>
      </mat-card>
    </div>
  `,
  styles: [`
    .passengers-container {
      padding: 24px 32px;
      display: flex;
      flex-direction: column;
      gap: 20px;
    }

    .header {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    .title-group {
      display: flex;
      align-items: center;
      gap: 16px;

      .icon-wrap {
        width: 48px;
        height: 48px;
        border-radius: 14px;
        background: linear-gradient(135deg, rgba(59, 130, 246, 0.2), rgba(99, 102, 241, 0.2));
        border: 1px solid rgba(59, 130, 246, 0.3);
        display: flex;
        align-items: center;
        justify-content: center;
        color: #60a5fa;

        mat-icon { font-size: 26px; width: 26px; height: 26px; }
      }

      h2 {
        margin: 0;
        font-size: 1.45rem;
        font-weight: 800;
        color: #f1f5f9;
      }

      .subtitle {
        margin: 2px 0 0;
        font-size: 0.85rem;
        color: #94a3b8;
      }
    }

    .refresh-btn {
      background: rgba(59, 130, 246, 0.15) !important;
      color: #60a5fa !important;
      border: 1px solid rgba(59, 130, 246, 0.3) !important;
      border-radius: 10px !important;
    }

    .filter-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
    }

    .search-field {
      flex: 1;
      max-width: 450px;

      ::ng-deep .mat-mdc-text-field-wrapper {
        background-color: #1e293b !important;
        border-radius: 12px;
      }
    }

    .stats-badge {
      background: rgba(30, 41, 59, 0.7);
      border: 1px solid rgba(255, 255, 255, 0.08);
      padding: 10px 18px;
      border-radius: 12px;
      font-size: 0.88rem;
      color: #94a3b8;

      b { color: #f8fafc; }
    }

    .table-card {
      background: rgba(30, 41, 59, 0.7);
      border: 1px solid rgba(255, 255, 255, 0.08);
      border-radius: 16px;
      overflow: hidden;
    }

    .table-container {
      overflow-x: auto;
      -webkit-overflow-scrolling: touch;
      width: 100%;
    }

    .dark-table {
      width: 100%;
      min-width: 680px;
      background: transparent !important;

      th {
        color: #94a3b8;
        font-weight: 700;
        font-size: 0.8rem;
        text-transform: uppercase;
        letter-spacing: 0.05em;
        border-bottom: 1px solid rgba(255, 255, 255, 0.08);
        padding: 16px 20px;
      }

      td {
        border-bottom: 1px solid rgba(255, 255, 255, 0.05);
        padding: 14px 20px;
        color: #f1f5f9;
        font-size: 0.9rem;
      }

      tr:hover td {
        background: rgba(255, 255, 255, 0.03);
      }
    }

    .user-cell {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .avatar {
      width: 36px;
      height: 36px;
      border-radius: 10px;
      background: linear-gradient(135deg, #3b82f6, #6366f1);
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 700;
      color: white;
      font-size: 0.88rem;
    }

    .user-details {
      display: flex;
      flex-direction: column;
    }

    .user-name {
      font-weight: 600;
      color: #f8fafc;
    }

    .user-id {
      font-size: 0.72rem;
      color: #64748b;
      font-family: monospace;
    }

    .phone-text {
      color: #38bdf8;
      font-weight: 600;
      font-family: monospace;
    }

    .email-text {
      color: #cbd5e1;
    }

    .trips-badge {
      background: rgba(16, 185, 129, 0.15);
      color: #34d399;
      border: 1px solid rgba(16, 185, 129, 0.3);
      padding: 4px 10px;
      border-radius: 20px;
      font-size: 0.78rem;
      font-weight: 600;
    }

    .date-text {
      color: #94a3b8;
      font-size: 0.85rem;
    }

    .whatsapp-btn {
      color: #22c55e !important;
      background: rgba(34, 197, 94, 0.1) !important;
      border-radius: 8px !important;

      &:hover {
        background: rgba(34, 197, 94, 0.25) !important;
      }
    }

    .no-action {
      color: #64748b;
    }

    .empty-cell {
      text-align: center;
      padding: 48px !important;
    }

    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
      color: #64748b;

      mat-icon { font-size: 40px; width: 40px; height: 40px; opacity: 0.5; }
      p { margin: 0; font-size: 0.9rem; }
    }

    .dark-paginator {
      background: transparent !important;
      color: #94a3b8 !important;
      border-top: 1px solid rgba(255, 255, 255, 0.05);
    }

    /* ── Responsive Media Queries ─────────────────────────── */
    @media (max-width: 991.98px) {
      .passengers-container {
        padding: 18px 16px;
        gap: 16px;
      }
    }

    @media (max-width: 640px) {
      .passengers-container {
        padding: 12px 10px;
        gap: 14px;
      }

      .header {
        flex-direction: column;
        align-items: stretch;
        gap: 14px;
      }

      .title-group h2 {
        font-size: 1.25rem;
      }

      .actions-group button {
        width: 100%;
        justify-content: center;
      }

      .filter-row {
        flex-direction: column;
        align-items: stretch;
        gap: 12px;
      }

      .search-field {
        max-width: 100%;
        width: 100%;
      }

      .stats-badge {
        text-align: center;
      }
    }
  `]
})
export class PassengersComponent implements OnInit {
  private readonly supabaseService = inject(SupabaseService);

  displayedColumns: string[] = ['name', 'phone', 'email', 'tripsCount', 'created_at', 'actions'];
  dataSource = new MatTableDataSource<PassengerProfile>([]);
  readonly totalCount = signal<number>(0);

  @ViewChild(MatPaginator) paginator!: MatPaginator;
  @ViewChild(MatSort) sort!: MatSort;

  async ngOnInit(): Promise<void> {
    await this.loadPassengers();
  }

  async loadPassengers(): Promise<void> {
    const data = await this.supabaseService.getPassengers();
    this.dataSource = new MatTableDataSource(data);
    this.totalCount.set(data.length);
    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;
  }

  applyFilter(event: Event): void {
    const filterValue = (event.target as HTMLInputElement).value;
    this.dataSource.filter = filterValue.trim().toLowerCase();
    if (this.dataSource.paginator) {
      this.dataSource.paginator.firstPage();
    }
  }

  openWhatsApp(passenger: PassengerProfile): void {
    if (!passenger.phone) return;
    const cleanPhone = passenger.phone.replace(/[^0-9]/g, '');
    const phoneWithCode = cleanPhone.startsWith('502') ? cleanPhone : `502${cleanPhone}`;
    const text = encodeURIComponent(
      `Hola ${passenger.name}, te saludamos de la administración de TucTucMap. ¿Tienes alguna duda o consulta sobre tus viajes?`
    );
    window.open(`https://wa.me/${phoneWithCode}?text=${text}`, '_blank');
  }
}
